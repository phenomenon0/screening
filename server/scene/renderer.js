import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

class WorldcastRenderer {
  constructor() {
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;
    document.body.appendChild(this.renderer.domElement);

    this.gltfLoader = new GLTFLoader();
    this.loadedObjects = new Map();
    this.selectedObject = null;

    // Camera target for SLERP interpolation
    this.targetQuaternion = new THREE.Quaternion();
    this.targetDistance = 5;
    this.orbitTarget = new THREE.Vector3(0, 0, 0);
    this.hasRemoteCamera = false;

    // Fallback orbit controls (for desktop/mouse testing)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.05;

    this.camera.position.set(0, 2, 5);
    this.controls.update();

    this.setupDefaultScene();
    this.connectWebSocket();
    this.animate();

    // Auto-load scene from URL param or default demo
    const params = new URLSearchParams(window.location.search);
    const sceneId = params.get('id') || 'demo';
    this.fetchAndLoadScene(sceneId);

    window.addEventListener('resize', () => this.onResize());
  }

  setupDefaultScene() {
    // Ambient light
    const ambient = new THREE.AmbientLight(0xffffff, 0.3);
    this.scene.add(ambient);

    // Directional light with shadows
    const dir = new THREE.DirectionalLight(0xffffff, 1.0);
    dir.position.set(5, 10, 5);
    dir.castShadow = true;
    dir.shadow.mapSize.set(1024, 1024);
    this.scene.add(dir);

    // Ground plane
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(20, 20),
      new THREE.MeshStandardMaterial({ color: 0x2a2a3e })
    );
    ground.rotation.x = -Math.PI / 2;
    ground.receiveShadow = true;
    this.scene.add(ground);
    this.ground = ground;

    // Grid helper
    const grid = new THREE.GridHelper(20, 20, 0x444466, 0x333355);
    this.scene.add(grid);
    this.grid = grid;

    // Default cube to show something
    const cube = new THREE.Mesh(
      new THREE.BoxGeometry(1, 1, 1),
      new THREE.MeshStandardMaterial({ color: 0x00D2FF, metalness: 0.3, roughness: 0.4 })
    );
    cube.position.y = 0.5;
    cube.castShadow = true;
    cube.userData.id = 'default_cube';
    cube.userData.selectable = true;
    this.scene.add(cube);
    this.loadedObjects.set('default_cube', cube);

    this.scene.background = new THREE.Color(0x0d0d0d);
    document.getElementById('loading').classList.add('hidden');
  }

  connectWebSocket() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${proto}//${location.host}/ws`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      console.log('WS connected');
      this.ws.send(JSON.stringify({
        type: 'register',
        device_type: 'scene_renderer',
        device_name: 'TV Scene Renderer'
      }));
    };

    this.ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data);
        this.handleMessage(msg);
      } catch (err) {
        console.error('Parse error:', err);
      }
    };

    this.ws.onclose = () => {
      console.log('WS disconnected, reconnecting...');
      setTimeout(() => this.connectWebSocket(), 2000);
    };

    this.ws.onerror = () => {};
  }

  handleMessage(msg) {
    switch (msg.type) {
      case 'scene_sync':
        this.loadScene(msg);
        break;
      case 'scene_camera_sync':
        if (msg.camera) {
          this.applyCameraUpdate(msg.camera);
        }
        break;
      case 'scene_object_selected':
        this.highlightObject(msg.objectId);
        break;
      case 'scene_load':
        this.fetchAndLoadScene(msg.scene_id);
        break;
      case 'ping':
        this.ws.send(JSON.stringify({ type: 'pong' }));
        break;
    }
  }

  async fetchAndLoadScene(sceneId) {
    if (!sceneId) return;
    try {
      document.getElementById('loading').classList.remove('hidden');
      const res = await fetch(`/scenes/${sceneId}`);
      const scene = await res.json();
      this.loadScene({ scene });
    } catch (err) {
      console.error('Failed to load scene:', err);
    }
  }

  async loadScene(msg) {
    const s = msg.scene;
    if (!s) return;

    // Clear existing objects
    for (const [id, obj] of this.loadedObjects) {
      this.scene.remove(obj);
    }
    this.loadedObjects.clear();

    // Environment
    if (s.environment) {
      if (s.environment.background) {
        this.scene.background = new THREE.Color(s.environment.background);
      }
    }

    // Camera
    if (s.camera) {
      const c = s.camera;
      if (c.position) this.camera.position.set(...c.position);
      if (c.target) this.orbitTarget.set(...c.target);
      if (c.fov) this.camera.fov = c.fov;
      this.camera.updateProjectionMatrix();
      this.controls.target.copy(this.orbitTarget);
      this.controls.update();
    }

    // Lights
    if (s.lights) {
      for (const l of s.lights) {
        let light;
        switch (l.type) {
          case 'directional':
            light = new THREE.DirectionalLight(new THREE.Color(l.color || '#fff'), l.intensity || 1);
            if (l.position) light.position.set(...l.position);
            light.castShadow = l.castShadow || false;
            break;
          case 'point':
            light = new THREE.PointLight(new THREE.Color(l.color || '#fff'), l.intensity || 1);
            if (l.position) light.position.set(...l.position);
            break;
          case 'ambient':
            light = new THREE.AmbientLight(new THREE.Color(l.color || '#fff'), l.intensity || 0.3);
            break;
        }
        if (light) {
          light.userData.id = l.id;
          this.scene.add(light);
          this.loadedObjects.set(l.id, light);
        }
      }
    }

    // Ground
    if (s.ground) {
      this.ground.visible = s.ground.enabled !== false;
      if (s.ground.color) this.ground.material.color.set(s.ground.color);
      this.grid.visible = s.ground.grid !== false;
    }

    // Objects (GLB models)
    if (s.objects) {
      for (const obj of s.objects) {
        try {
          const gltf = await this.loadGLB(`/assets/${obj.asset}`);
          const model = gltf.scene;
          if (obj.position) model.position.set(...obj.position);
          if (obj.rotation) model.rotation.set(
            THREE.MathUtils.degToRad(obj.rotation[0]),
            THREE.MathUtils.degToRad(obj.rotation[1]),
            THREE.MathUtils.degToRad(obj.rotation[2])
          );
          if (obj.scale) model.scale.set(...obj.scale);
          model.userData.id = obj.id;
          model.userData.name = obj.name;
          model.userData.selectable = obj.selectable !== false;
          model.traverse(child => {
            if (child.isMesh) {
              child.castShadow = true;
              child.receiveShadow = true;
            }
          });
          this.scene.add(model);
          this.loadedObjects.set(obj.id, model);
        } catch (err) {
          console.warn(`Failed to load ${obj.asset}:`, err);
        }
      }
    }

    document.getElementById('loading').classList.add('hidden');
  }

  loadGLB(url) {
    return new Promise((resolve, reject) => {
      this.gltfLoader.load(url, resolve, undefined, reject);
    });
  }

  applyCameraUpdate(cam) {
    this.hasRemoteCamera = true;
    this.controls.enabled = false; // disable orbit controls when remote-controlled

    if (cam.quaternion) {
      this.targetQuaternion.set(cam.quaternion[0], cam.quaternion[1], cam.quaternion[2], cam.quaternion[3]);
    }
    if (cam.distance !== undefined) {
      this.targetDistance = cam.distance;
    }
    if (cam.target) {
      this.orbitTarget.set(cam.target[0], cam.target[1], cam.target[2]);
    }
  }

  highlightObject(objectId) {
    // Remove previous highlight
    if (this.selectedObject) {
      this.selectedObject.traverse(child => {
        if (child.isMesh && child.userData._origEmissive !== undefined) {
          child.material.emissive.setHex(child.userData._origEmissive);
        }
      });
    }

    const obj = this.loadedObjects.get(objectId);
    if (obj) {
      this.selectedObject = obj;
      obj.traverse(child => {
        if (child.isMesh) {
          child.userData._origEmissive = child.material.emissive.getHex();
          child.material.emissive.setHex(0x00D2FF);
          child.material.emissiveIntensity = 0.3;
        }
      });
    }
  }

  animate() {
    requestAnimationFrame(() => this.animate());

    if (this.hasRemoteCamera) {
      // SLERP camera rotation from phone quaternion
      this.camera.quaternion.slerp(this.targetQuaternion, 0.15);

      // Position camera at distance from target along the quaternion direction
      const dir = new THREE.Vector3(0, 0, 1).applyQuaternion(this.camera.quaternion);
      this.camera.position.copy(this.orbitTarget).add(dir.multiplyScalar(this.targetDistance));
    } else {
      this.controls.update();
    }

    this.renderer.render(this.scene, this.camera);
  }

  onResize() {
    this.camera.aspect = window.innerWidth / window.innerHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(window.innerWidth, window.innerHeight);
  }
}

// Boot
new WorldcastRenderer();
