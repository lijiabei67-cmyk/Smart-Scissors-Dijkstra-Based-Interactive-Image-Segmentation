# Intelligent Scissors: Interactive Image Matting via Dijkstra Shortest Path

An interactive image segmentation tool that lets you "cut out" objects by tracing their edges with a few clicks. Built on **Dijkstra's shortest path algorithm** over a **Sobel-gradient–weighted pixel graph** — the path automatically snaps to the strongest edge between the points you place.

![Java](https://img.shields.io/badge/Java-8%2B-orange)
![Swing](https://img.shields.io/badge/UI-Swing-lightgrey)

---

## Demo

1. Load an image (JPG / PNG / BMP / GIF)
2. Click **"智能抠图"** to enter matting mode
3. Click on the boundary to drop seed points
4. Press **Space** to close the loop — Dijkstra fills in the segments and exports a **transparent PNG**

---

## How It Works

| Step | Description |
|------|-------------|
| **Gradient Map** | Sobel operator (3×3 kernel) computes per-pixel edge magnitude on the grayscale channel. High gradient = strong edge. |
| **Weighted Graph** | Every pixel becomes a graph node. Each node connects to its 4 neighbors with edge cost `cost = 1000 / (1 + max(G[p₁], G[p₂]))` — strong edges become *cheap* paths. |
| **Dijkstra Search** | When you press Space, Dijkstra runs between consecutive seed points (and back to the start to close the loop), naturally hugging high-gradient pixels. |
| **Polygon Clip** | The closed path defines a polygon clip region; pixels inside are kept, everything else becomes transparent. Transparent borders are auto-cropped on save. |

### Why 4-connected (not 8)?

8-connected graphs theoretically yield smoother diagonals, but cost ~2× the edges and double Dijkstra's runtime. On boundaries that are anti-aliased anyway, 4-connectivity produces visually indistinguishable paths at half the cost — a deliberate trade-off after benchmarking on test images.

### Complexity

- **Gradient + graph construction:** `O(W·H)` — done once on image load, on a background thread
- **Dijkstra per segment:** `O(N log N)` where `N = W·H`, with **early exit** when the target pixel is dequeued
- **Overall:** practical for images up to ~2000×2000 on a modern laptop

---

## Project Structure

```
.
├── README.md
└── coding/
    └── IntelligentScissor.java   ★ Main application (GUI + algorithm, single-file runnable)
```

**Entry point:** `coding/IntelligentScissor.java`. The single file owns the Swing GUI, the Sobel gradient computation, the graph builder, and a self-contained `Dijkstra` + `Node` as static nested classes — no internal dependencies beyond `algs4.jar`.

---

## Features

- **Edge-snapping path** — Dijkstra over the gradient graph means you can be sloppy with your clicks; the path finds the real edge
- **Background gradient precomputation** — graph is built on a worker thread the moment an image loads, so matting feels instant
- **Live preview** — start (green dot) and current cursor (blue dot) render on a separate display layer; the original image is never mutated
- **Auto-close + auto-crop** — pressing Space runs one final Dijkstra back to the start point, then crops transparent borders before exporting PNG
- **Reset semantics** — `resetSelection()` cancels in-flight Dijkstra threads, removes mouse/key listeners, and rebuilds the display image to prevent listener leaks

---

## Dependencies

- **JDK 8+**
- **[algs4.jar](https://algs4.cs.princeton.edu/code/algs4.jar)** — Princeton's algorithms library, used only for `MinPQ<Node>` (the indexed binary heap backing Dijkstra)

---

## How to Run

```bash
# 1. Download algs4.jar into the coding/ directory
#    https://algs4.cs.princeton.edu/code/algs4.jar

# 2. Compile
cd coding
javac -cp algs4.jar IntelligentScissor.java

# 3. Run  (Windows: ; separator,  Linux/macOS: : separator)
java -cp ".;algs4.jar" IntelligentScissor    # Windows
java -cp ".:algs4.jar" IntelligentScissor    # Linux / macOS
```

---

## Usage

1. **"上传图片"** — load an image
2. **"智能抠图"** — enter matting mode (status bar prompts the next action)
3. Click to place the **start point** (green)
4. Click to add intermediate seed points along the boundary
5. Press **Space** — Dijkstra fills in the optimal path between every consecutive pair (and closes the loop), then runs the polygon clip
6. **File ▸ 保存** — export the cropped transparent PNG
7. **"重置"** — clear all state and start over

---

## Architecture

```
┌────────────────┐    ┌──────────────────────┐    ┌────────────────────┐
│   View         │    │   Controller         │    │   Model            │
│   (Swing)      │───▶│   (Event Listeners)  │───▶│                    │
│                │    │                      │    │  BufferedImage     │
│   ImagePanel   │    │   MouseAdapter       │    │  int[][] G         │
│   JFrame       │    │   KeyAdapter         │    │  List<List<Node>>  │
│   JButton      │    │   ActionListener     │    │  List<Point>       │
│   JMenu        │    │   Background Workers │    │                    │
│                │◀───│   (gradient + path)  │◀───│                    │
└────────────────┘    └──────────────────────┘    └────────────────────┘
```

Three concerns kept cleanly separated inside one file: Swing handles render, lambda-based listeners translate user input into model mutations, and the model holds only raw image data + the gradient/graph/path triple.
