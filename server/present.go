package main

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync"
)

type PresentPage struct {
	Number int    `json:"number"`
	URL    string `json:"url"`
}

type Presentation struct {
	ID        string        `json:"id"`
	Title     string        `json:"title"`
	PageCount int           `json:"page_count"`
	Current   int           `json:"current"`
	Pages     []PresentPage `json:"pages"`
}

type PresentationStore struct {
	mu       sync.RWMutex
	active   *Presentation
	dataDir  string
	nextID   int
	onChange func()
}

func NewPresentationStore(dataDir string, onChange func()) *PresentationStore {
	dir := filepath.Join(dataDir, "presentations")
	os.MkdirAll(dir, 0755)
	return &PresentationStore{dataDir: dir, onChange: onChange}
}

func (ps *PresentationStore) Active() *Presentation {
	ps.mu.RLock()
	defer ps.mu.RUnlock()
	return ps.active
}

func (ps *PresentationStore) SetPage(page int) {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	if ps.active == nil {
		return
	}
	if page < 0 {
		page = 0
	}
	if page >= ps.active.PageCount {
		page = ps.active.PageCount - 1
	}
	ps.active.Current = page
}

func (ps *PresentationStore) Close() {
	ps.mu.Lock()
	defer ps.mu.Unlock()
	if ps.active != nil {
		// Clean up old presentation files
		os.RemoveAll(filepath.Join(ps.dataDir, ps.active.ID))
		ps.active = nil
	}
}

// Upload handles a file upload: saves it, converts to page images, sets as active.
func (ps *PresentationStore) Upload(filename string, data []byte) (*Presentation, error) {
	ps.mu.Lock()
	defer ps.mu.Unlock()

	// Clean up previous
	if ps.active != nil {
		os.RemoveAll(filepath.Join(ps.dataDir, ps.active.ID))
	}

	ps.nextID++
	id := fmt.Sprintf("p%d", ps.nextID)
	dir := filepath.Join(ps.dataDir, id)
	os.MkdirAll(dir, 0755)

	// Save source file in a subdirectory to avoid name collisions with output pages
	srcDir := filepath.Join(dir, "_src")
	os.MkdirAll(srcDir, 0755)
	ext := strings.ToLower(filepath.Ext(filename))
	srcPath := filepath.Join(srcDir, "source"+ext)
	if err := os.WriteFile(srcPath, data, 0644); err != nil {
		return nil, fmt.Errorf("save source: %w", err)
	}

	// Convert based on type
	var err error
	switch ext {
	case ".pdf":
		err = convertPDF(srcPath, dir)
	case ".pptx", ".ppt", ".odp", ".docx", ".doc":
		err = convertOffice(srcPath, dir)
	case ".cbz":
		err = extractCBZ(srcPath, dir)
	case ".cbr":
		err = extractCBR(srcPath, dir)
	case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp":
		err = importImage(srcPath, dir)
	default:
		return nil, fmt.Errorf("unsupported format: %s", ext)
	}
	if err != nil {
		return nil, fmt.Errorf("convert: %w", err)
	}

	// Collect page images
	pages := collectPages(id, dir)
	if len(pages) == 0 {
		return nil, fmt.Errorf("no pages generated from %s", filename)
	}

	title := strings.TrimSuffix(filepath.Base(filename), ext)
	pres := &Presentation{
		ID:        id,
		Title:     title,
		PageCount: len(pages),
		Current:   0,
		Pages:     pages,
	}
	ps.active = pres
	log.Printf("present: loaded %q — %d pages", title, len(pages))
	return pres, nil
}

// collectPages finds all page images in a directory, sorted by name.
func collectPages(id, dir string) []PresentPage {
	entries, _ := os.ReadDir(dir)
	var pages []PresentPage
	imageExts := map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".gif": true, ".webp": true, ".bmp": true}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		ext := strings.ToLower(filepath.Ext(e.Name()))
		if !imageExts[ext] {
			continue
		}
		pages = append(pages, PresentPage{
			URL: fmt.Sprintf("/present/%s/%s", id, e.Name()),
		})
	}
	sort.Slice(pages, func(i, j int) bool { return pages[i].URL < pages[j].URL })
	for i := range pages {
		pages[i].Number = i
	}
	return pages
}

func convertPDF(src, destDir string) error {
	// pdftoppm -png -r 200 input.pdf output_prefix
	prefix := filepath.Join(destDir, "page")
	cmd := exec.Command("pdftoppm", "-png", "-r", "200", src, prefix)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("pdftoppm: %v — %s", err, out)
	}
	return nil
}

func convertOffice(src, destDir string) error {
	// Step 1: convert to PDF
	cmd := exec.Command("libreoffice", "--headless", "--convert-to", "pdf", "--outdir", destDir, src)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("libreoffice: %v — %s", err, out)
	}
	// Find the generated PDF
	base := strings.TrimSuffix(filepath.Base(src), filepath.Ext(src))
	pdfPath := filepath.Join(destDir, base+".pdf")
	if _, err := os.Stat(pdfPath); err != nil {
		// Try source.pdf
		pdfPath = filepath.Join(destDir, "source.pdf")
		if _, err := os.Stat(pdfPath); err != nil {
			return fmt.Errorf("no PDF generated")
		}
	}
	// Step 2: PDF to images
	return convertPDF(pdfPath, destDir)
}

func extractCBZ(src, destDir string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer r.Close()

	imageExts := map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".gif": true, ".webp": true, ".bmp": true}
	num := 0
	for _, f := range r.File {
		ext := strings.ToLower(filepath.Ext(f.Name))
		if !imageExts[ext] {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			continue
		}
		num++
		outPath := filepath.Join(destDir, fmt.Sprintf("page-%03d%s", num, ext))
		out, err := os.Create(outPath)
		if err != nil {
			rc.Close()
			continue
		}
		io.Copy(out, rc)
		out.Close()
		rc.Close()
	}
	return nil
}

func extractCBR(src, destDir string) error {
	cmd := exec.Command("unrar", "x", "-o+", src, destDir+"/")
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("unrar: %v — %s", err, out)
	}
	// Rename extracted images to sequential page-NNN names
	imageExts := map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".gif": true, ".webp": true, ".bmp": true}
	entries, _ := os.ReadDir(destDir)
	var images []string
	for _, e := range entries {
		ext := strings.ToLower(filepath.Ext(e.Name()))
		if imageExts[ext] && !strings.HasPrefix(e.Name(), "source") {
			images = append(images, e.Name())
		}
	}
	sort.Strings(images)
	for i, name := range images {
		ext := filepath.Ext(name)
		newName := fmt.Sprintf("page-%03d%s", i+1, ext)
		if name != newName {
			os.Rename(filepath.Join(destDir, name), filepath.Join(destDir, newName))
		}
	}
	return nil
}

func importImage(src, destDir string) error {
	ext := filepath.Ext(src)
	dst := filepath.Join(destDir, "page-001"+ext)
	data, err := os.ReadFile(src)
	if err != nil {
		return err
	}
	return os.WriteFile(dst, data, 0644)
}

// HTTP handler: POST /api/present
func handlePresentUpload(ps *PresentationStore, hub *Hub) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST only", http.StatusMethodNotAllowed)
			return
		}
		r.ParseMultipartForm(200 << 20) // 200MB
		file, header, err := r.FormFile("file")
		if err != nil {
			http.Error(w, "no file: "+err.Error(), http.StatusBadRequest)
			return
		}
		defer file.Close()

		data, err := io.ReadAll(file)
		if err != nil {
			http.Error(w, "read error", http.StatusInternalServerError)
			return
		}

		pres, err := ps.Upload(header.Filename, data)
		if err != nil {
			http.Error(w, "convert error: "+err.Error(), http.StatusInternalServerError)
			return
		}

		// Broadcast to all clients
		hub.Broadcast(ServerMessage{Type: "presentation_sync", Present: pres})
		// Also switch TV to presentation frame
		hub.SendToType("tv", ServerMessage{Type: "frame_change", Frame: 7})

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(pres)
	}
}

// HTTP handler: /present/{id}/{file}
func handlePresentServe(ps *PresentationStore) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// URL: /present/p1/page-001.png
		path := strings.TrimPrefix(r.URL.Path, "/present/")
		parts := strings.SplitN(path, "/", 2)
		if len(parts) != 2 {
			http.Error(w, "bad path", http.StatusBadRequest)
			return
		}
		filePath := filepath.Join(ps.dataDir, parts[0], filepath.Base(parts[1]))
		http.ServeFile(w, r, filePath)
	}
}
