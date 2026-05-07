import edu.princeton.cs.algs4.MinPQ;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IntelligentScissor {
    private JFrame frame;
    private ImagePanel imagePanel;
    private BufferedImage originalImage;
    private BufferedImage scaledImage;
    private BufferedImage displayImage;
    private BufferedImage result;
    private int[][] G; // Gradient matrix
    private List<List<Node>> graph; // Graph representation using adjacency list
    private List<Point> pathPoints = new ArrayList<>();
    private Point startPoint = null;
    private Point currentEndPoint = null; // Track current end point
    private JLabel statusLabel;
    private double scaleFactor = 1.0;
    private boolean inMattingMode = false;
    private KeyAdapter keyAdapter;
    private Thread pathCalculationThread;//存储路径计算线程

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new IntelligentScissor().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Intelligent Scissors");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Menu Bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem saveItem = new JMenuItem("保存");
        JMenuItem exitItem = new JMenuItem("退出");
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu helpMenu = new JMenu("帮助");
        JMenuItem aboutItem = new JMenuItem("操作说明");
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        frame.setJMenuBar(menuBar);

        // Top Panel
        JPanel topPanel = new JPanel();
        topPanel.setBackground(new Color(230, 230, 250));
        topPanel.setLayout(new FlowLayout());

        JButton uploadButton = new JButton("上传图片");
        JButton mattingButton = new JButton("智能抠图");
        JButton resetButton = new JButton("重置");

        topPanel.add(uploadButton);
        topPanel.add(mattingButton);
        topPanel.add(resetButton);
        frame.add(topPanel, BorderLayout.NORTH);

        // Center Panel
        imagePanel = new ImagePanel();
        imagePanel.setBackground(Color.WHITE);
        frame.add(new JScrollPane(imagePanel), BorderLayout.CENTER);

        // Bottom Panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("就绪");
        bottomPanel.add(statusLabel);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Event Listeners
        uploadButton.addActionListener(e -> loadImage());
        saveItem.addActionListener(e -> saveResultImage());
        mattingButton.addActionListener(e -> {
            if (originalImage == null) {
                JOptionPane.showMessageDialog(frame, "请先上传图片", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            statusLabel.setText("请点击图片选择起点和终点，按空格键完成选区");
            setupMattingMode();
        });

        resetButton.addActionListener(e -> resetSelection());

        exitItem.addActionListener(e -> System.exit(0));

        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "1. 点击'智能抠图'按钮\n2. 在图片上点击选择起点\n3. 点击选择终点自动生成路径\n4. 重复步骤3继续添加路径点\n5. 按空格键完成选区并抠图",
                "操作说明", JOptionPane.INFORMATION_MESSAGE));

        frame.setVisible(true);
    }

    private void loadImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                String name = f.getName().toLowerCase();
                return f.isDirectory() ||
                        name.endsWith(".jpg") ||
                        name.endsWith(".jpeg") ||
                        name.endsWith(".png") ||
                        name.endsWith(".gif") ||
                        name.endsWith(".bmp");
            }

            public String getDescription() {
                return "图片文件 (*.jpg, *.jpeg, *.png, *.gif, *.bmp)";
            }
        });

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                originalImage = ImageIO.read(selectedFile);
                if (originalImage != null) {
                    resetSelection();
                    calculateScaledImage();
                    statusLabel.setText("已加载图片: " + selectedFile.getName());

                    // Precompute gradient matrix in background
                    new Thread(() -> {
                        G = getGMatrixFromImage(originalImage);
                        graph = buildGraph(originalImage.getWidth(), originalImage.getHeight(), G);
                    }).start();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "无法加载图片: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("加载图片失败");
            }
        }
    }

    private void calculateScaledImage() {
        int panelWidth = imagePanel.getWidth() - 20;
        int panelHeight = imagePanel.getHeight() - 20;

        double widthRatio = (double) panelWidth / originalImage.getWidth();
        double heightRatio = (double) panelHeight / originalImage.getHeight();
        scaleFactor = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (originalImage.getWidth() * scaleFactor);
        int newHeight = (int) (originalImage.getHeight() * scaleFactor);

        scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        displayImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        g2d = displayImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        imagePanel.setImage(displayImage);
    }

    private void setupMattingMode() {
        resetSelection();
        inMattingMode = true;

        // Mouse listener for point selection
        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!inMattingMode) return;

                if (startPoint == null) {
                    // Set start point
                    startPoint = convertToImageCoordinates(e.getPoint());
                    pathPoints.add(startPoint);
                    statusLabel.setText("起点已设置，请点击选择路径点，按空格键完成选区");
                    drawPointsOnImage();
                } else {
                    // Just add the point to pathPoints without calculating path
                    Point newPoint = convertToImageCoordinates(e.getPoint());
                    pathPoints.add(newPoint);
                    drawPointsOnImage();
                    drawPathOnImage();
                    statusLabel.setText("路径点已添加，请继续添加或按空格键完成选区");
                }
            }
        });

        // Mouse motion listener to show current point
        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!inMattingMode || startPoint == null) return;

                currentEndPoint = convertToImageCoordinates(e.getPoint());
                drawPointsOnImage();
            }
        });

        // Keyboard listener for completing selection
        keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && !pathPoints.isEmpty() && inMattingMode) {
                    // Complete selection by closing the path
                    if (pathPoints.size() >= 2) {
                        Point firstPoint = pathPoints.get(0);
                        Point lastPoint = pathPoints.get(pathPoints.size() - 1);

                        // Only calculate path if last point is not already close to first point
                        if (firstPoint.distance(lastPoint) > 5) {
                            currentEndPoint = firstPoint;

                            new Thread(() -> {
                                List<Integer> closingPath = findPath(lastPoint, firstPoint);
                                SwingUtilities.invokeLater(() -> {
                                    if (closingPath != null && !closingPath.isEmpty()) {
                                        for (int index : closingPath) {
                                            int x = index % originalImage.getWidth();
                                            int y = index / originalImage.getWidth();
                                            pathPoints.add(new Point(x, y));
                                        }
                                    }
                                    drawPointsOnImage();
                                    drawPathOnImage();
                                    performMatting();
                                    inMattingMode = false;
                                });
                            }).start();
                        } else {
                            // Points are already close, just complete the selection
                            performMatting();
                            inMattingMode = false;
                        }
                    } else {
                        JOptionPane.showMessageDialog(frame, "至少需要2个点才能形成选区", "提示", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        };
        imagePanel.setFocusable(true);
        imagePanel.requestFocusInWindow();
        imagePanel.addKeyListener(keyAdapter);
    }

    private void resetSelection() {
        if (pathCalculationThread != null && pathCalculationThread.isAlive()) {
            pathCalculationThread.interrupt();
        }
        pathCalculationThread = null;

        pathPoints.clear();
        startPoint = null;
        currentEndPoint = null;
        inMattingMode = false;

        // Remove all listeners to prevent memory leaks
        for (MouseListener ml : imagePanel.getMouseListeners()) {
            imagePanel.removeMouseListener(ml);
        }
        for (MouseMotionListener mml : imagePanel.getMouseMotionListeners()) {
            imagePanel.removeMouseMotionListener(mml);
        }
        if (keyAdapter != null) {
            imagePanel.removeKeyListener(keyAdapter);
            keyAdapter = null;
        }

        if (originalImage != null) {
            calculateScaledImage();
            imagePanel.repaint();
        }
        statusLabel.setText("就绪");
    }

    private Point convertToImageCoordinates(Point panelPoint) {
        int panelWidth = imagePanel.getWidth();
        int panelHeight = imagePanel.getHeight();
        int imgX = (panelWidth - scaledImage.getWidth()) / 2;
        int imgY = (panelHeight - scaledImage.getHeight()) / 2;

        int x = (int) ((panelPoint.x - imgX) / scaleFactor);
        int y = (int) ((panelPoint.y - imgY) / scaleFactor);

        x = Math.max(0, Math.min(x, originalImage.getWidth() - 1));
        y = Math.max(0, Math.min(y, originalImage.getHeight() - 1));

        return new Point(x, y);
    }

    private void drawPointsOnImage() {
        // Create a copy of the scaled image
        BufferedImage newDisplayImage = new BufferedImage(
                scaledImage.getWidth(),
                scaledImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = newDisplayImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);

        // Draw start point (green)
        if (startPoint != null) {
            Point scaledStart = scalePoint(startPoint);
            g2d.setColor(Color.GREEN);
            g2d.fillOval(scaledStart.x - 5, scaledStart.y - 5, 10, 10);
        }

        // Draw current end point (blue)
        if (currentEndPoint != null) {
            Point scaledEnd = scalePoint(currentEndPoint);
            g2d.setColor(Color.BLUE);
            g2d.fillOval(scaledEnd.x - 5, scaledEnd.y - 5, 10, 10);
        }

        g2d.dispose();
        displayImage = newDisplayImage;
        imagePanel.setImage(displayImage);
    }

    private void drawPathOnImage() {
        if (displayImage == null) return;

        // Create a copy of the current display image
        BufferedImage newDisplayImage = new BufferedImage(
                displayImage.getWidth(),
                displayImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = newDisplayImage.createGraphics();
        g2d.drawImage(displayImage, 0, 0, null);

        // Draw path (red)
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(2));

        if (pathPoints.size() > 1) {
            Point prev = scalePoint(pathPoints.get(0));
            for (int i = 1; i < pathPoints.size(); i++) {
                Point current = scalePoint(pathPoints.get(i));
                g2d.drawLine(prev.x, prev.y, current.x, current.y);
                prev = current;
            }
        }

        g2d.dispose();
        displayImage = newDisplayImage;
        imagePanel.setImage(displayImage);
    }

    private Point scalePoint(Point p) {
        return new Point(
                (int) (p.x * scaleFactor),
                (int) (p.y * scaleFactor));
    }

    private List<Integer> findPath(Point start, Point end) {
        if (graph == null) return new ArrayList<>();

        int startIndex = start.y * originalImage.getWidth() + start.x;
        int endIndex = end.y * originalImage.getWidth() + end.x;

        return Dijkstra.dijkstra(graph, startIndex, endIndex);
    }

    private void performMatting() {
        if (pathPoints.size() < 3) {
            JOptionPane.showMessageDialog(frame, "至少需要3个点才能形成选区", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create result image
        result = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        // Create polygon from path points
        Polygon polygon = new Polygon();
        for (Point p : pathPoints) {
            polygon.addPoint(p.x, p.y);
        }

        // Fill polygon with original image data
        Graphics2D g2d = result.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 0));
        g2d.fillRect(0, 0, result.getWidth(), result.getHeight());
        g2d.setClip(polygon);
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();

        // Show result
        showResultImage(result);
    }

    private void saveResultImage() {
        if (result == null) {
            JOptionPane.showMessageDialog(frame, "请先完成抠图", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        BufferedImage croppedResult = cropTransparentEdge(result);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                String name = f.getName().toLowerCase();
                return f.isDirectory() || name.endsWith(".png");
            }

            public String getDescription() {
                return "PNG 图片(*.png)";
            }
        });
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".png")) {
                selectedFile = new File(filePath + ".png");
            }
            try {
                ImageIO.write(croppedResult, "png", selectedFile);
                JOptionPane.showMessageDialog(frame, "图片保存成功", "提示", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "图片保存时出错：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showResultImage(BufferedImage image) {
        BufferedImage croppedResult = cropTransparentEdge(image);
        JFrame resultFrame = new JFrame("抠图结果");
        resultFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Scale result image for display
        int newWidth = (int) (croppedResult.getWidth() * 0.5);
        int newHeight = (int) (croppedResult.getHeight() * 0.5);
        BufferedImage scaledResult = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledResult.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(croppedResult, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        resultFrame.add(new JLabel(new ImageIcon(scaledResult)));
        resultFrame.pack();
        resultFrame.setVisible(true);

        statusLabel.setText("抠图完成");
    }

    //cut off transparent edges
    private BufferedImage cropTransparentEdge(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int left = width;
        int right = 0;
        int top = height;
        int bottom = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xff;
                if (alpha > 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (right >= left && bottom >= top) {
            return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
        }
        return image;
    }

    // Optimized graph building - only consider 4-connected neighbors instead of 8
    private List<List<Node>> buildGraph(int width, int height, int[][] G) {
        int n = width * height;
        List<List<Node>> graph = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;

                // Only consider 4-connected neighbors (up, down, left, right)
                int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};

                for (int[] dir : directions) {
                    int nx = x + dir[0];
                    int ny = y + dir[1];

                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        int neighborIndex = ny * width + nx;
                        // Use the maximum gradient value between the two pixels
                        int cost = 1000 / (1 + Math.max(G[y][x], G[ny][nx]));
                        graph.get(index).add(new Node(neighborIndex, cost));
                    }
                }
            }
        }
        return graph;
    }

    public static int[][] getGMatrixFromImage(BufferedImage bimg) {
        int width = bimg.getWidth();
        int height = bimg.getHeight();
        int[][] G = new int[height][width];

        // Precompute grayscale values for faster access
        int[][] gray = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bimg.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = (r + g + b) / 3; // Simple grayscale conversion
            }
        }

        // Sobel kernels
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;

                // Apply Sobel operators
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int val = gray[y + ky][x + kx];
                        gx += val * sobelX[ky + 1][kx + 1];
                        gy += val * sobelY[ky + 1][kx + 1];
                    }
                }

                // Calculate gradient magnitude
                G[y][x] = (int) Math.sqrt(gx * gx + gy * gy);
                G[y][x] = Math.min(255, G[y][x]); // Clamp to 255
            }
        }

        // Handle borders by copying adjacent values
        for (int x = 0; x < width; x++) {
            G[0][x] = G[1][x];
            G[height - 1][x] = G[height - 2][x];
        }
        for (int y = 0; y < height; y++) {
            G[y][0] = G[y][1];
            G[y][width - 1] = G[y][width - 2];
        }

        return G;
    }

    static class Dijkstra {
        private static final int INF = Integer.MAX_VALUE;

        public static List<Integer> dijkstra(List<List<Node>> graph, int start, int end) {
            int n = graph.size();
            int[] dist = new int[n];
            int[] prev = new int[n];
            boolean[] visited = new boolean[n];

            for (int i = 0; i < n; i++) {
                dist[i] = INF;
                prev[i] = -1;
            }
            dist[start] = 0;

            MinPQ<Node> pq = new MinPQ<>();
            pq.insert(new Node(start, 0));

            while (!pq.isEmpty()) {
                Node current = pq.delMin();
                int u = current.id;

                if (u == end) break; // Early exit if we've reached the target
                if (visited[u]) continue;
                visited[u] = true;

                for (Node neighbor : graph.get(u)) {
                    int v = neighbor.id;
                    int weight = neighbor.dist;
                    if (!visited[v]) {
                        int alt = dist[u] + weight;
                        if (alt < dist[v]) {
                            dist[v] = alt;
                            prev[v] = u;
                            pq.insert(new Node(v, alt));
                        }
                    }
                }
            }

            // Reconstruct path
            List<Integer> path = new ArrayList<>();
            if (prev[end] == -1) return path; // No path found

            for (int at = end; at != -1; at = prev[at]) {
                path.add(at);
            }
            java.util.Collections.reverse(path);

            return path;
        }
    }

    static class Node implements Comparable<Node> {
        int id;
        int dist;

        public Node(int id, int dist) {
            this.id = id;
            this.dist = dist;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.dist, other.dist);
        }
    }

    class ImagePanel extends JPanel {
        private BufferedImage image;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                int x = (getWidth() - image.getWidth()) / 2;
                int y = (getHeight() - image.getHeight()) / 2;
                g.drawImage(image, x, y, this);
            }
        }

        public void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
            return new Dimension(600, 400);
        }
    }
}