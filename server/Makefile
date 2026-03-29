VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
COMMIT  ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo "none")
DATE    ?= $(shell date -u +%Y-%m-%dT%H:%M:%SZ)
LDFLAGS  = -s -w -X main.version=$(VERSION) -X main.commit=$(COMMIT) -X main.buildDate=$(DATE)

BINARY   = screening-server
DIST     = dist

.PHONY: build clean release install

build:
	go build -ldflags "$(LDFLAGS)" -o $(BINARY) .

install: build
	cp $(BINARY) ~/.local/bin/$(BINARY)

clean:
	rm -rf $(BINARY) $(DIST)

release: clean
	@mkdir -p $(DIST)
	@echo "Building for all platforms..."
	GOOS=linux   GOARCH=amd64 go build -ldflags "$(LDFLAGS)" -o $(DIST)/$(BINARY)-linux-amd64 .
	GOOS=linux   GOARCH=arm64 go build -ldflags "$(LDFLAGS)" -o $(DIST)/$(BINARY)-linux-arm64 .
	@echo ""
	@for f in $(DIST)/*; do \
		echo "  $$(du -h $$f | cut -f1)  $$f"; \
	done
	@echo ""
	@echo "Packaging tarballs..."
	@cd $(DIST) && for f in $(BINARY)-linux-*; do \
		tar czf $$f.tar.gz $$f ../config.json ../screening-server.service && rm $$f; \
	done
	@echo "Done:"
	@ls -lh $(DIST)/
