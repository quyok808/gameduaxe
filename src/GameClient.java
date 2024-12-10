
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameClient {

    private static final String SERVER_ADDRESS = "25.33.107.197";
    private static final int SERVER_PORT = 9876;
    private static final int FINISH_LINE = 650; // Vạch đích
    private static Map<String, Integer> playerCooldowns = new ConcurrentHashMap<>(); // Lưu thời gian hồi chiêu của từng người chơi
    private static final int BOOST_COOLDOWN = 5000; // Thời gian hồi chiêu của BOOST (5 giây)

    private DatagramSocket socket;
    private InetAddress serverAddress;

    private JFrame frame;
    private RacePanel racePanel;

    private String playerName;
    private ConcurrentHashMap<String, Integer> playerPositions = new ConcurrentHashMap<>();

    public GameClient(String playerName) {
        this.playerName = playerName;
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_ADDRESS);

            // Gửi thông điệp thông báo người chơi mới
            String newPlayerMessage = playerName + ":NEW_PLAYER";
            DatagramPacket sendPacket = new DatagramPacket(newPlayerMessage.getBytes(), newPlayerMessage.length(), serverAddress, SERVER_PORT);
            socket.send(sendPacket);

            initUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initUI() {
        frame = new JFrame("Racing Client - " + playerName);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        racePanel = new RacePanel(playerPositions, playerName);
        frame.add(racePanel);

        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                String action = null;

                // Kiểm tra nếu phím SHIFT đang được nhấn
                if (e.isShiftDown()) {
                    // Nếu SHIFT được nhấn, gửi lệnh BOOST
                    action = "BOOST";
                } else {
                    // Nếu không nhấn SHIFT, kiểm tra các phím di chuyển
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP:
                            action = "MOVE_FORWARD";
                            break;
                        case KeyEvent.VK_DOWN:
                            action = "MOVE_BACKWARD"; // Di chuyển lùi
                            break;
                        case KeyEvent.VK_LEFT:
                            action = "TURN_LEFT";
                            break;
                        case KeyEvent.VK_RIGHT:
                            action = "TURN_RIGHT";
                            break;
                    }
                }
                System.out.println("Key pressed: " + e.getKeyCode() + ", action: " + action);
                if (action != null) {
                    sendAction(action);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                try {
                    String leaveMessage = playerName + ":PLAYER_LEFT";
                    DatagramPacket leavePacket = new DatagramPacket(leaveMessage.getBytes(), leaveMessage.length(), serverAddress, SERVER_PORT);
                    socket.send(leavePacket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.exit(0); // Đóng ứng dụng khi người chơi rời
            }
        });

        frame.setVisible(true);
        frame.setFocusable(true);
        frame.requestFocus();
        startListening();
    }

    private void sendAction(String action) {
        if (action.equals("BOOST")) {
            // Kiểm tra nếu thời gian hồi chiêu chưa hết
            Integer lastUsedTime = playerCooldowns.get(playerName);
            long currentTime = System.currentTimeMillis();

            if (lastUsedTime != null && currentTime - lastUsedTime < BOOST_COOLDOWN) {
                System.out.println("BOOST is on cooldown. Try again later.");
                return; // Nếu đang trong thời gian hồi chiêu, không thực hiện hành động
            }

            // Cập nhật thời gian dùng BOOST
            playerCooldowns.put(playerName, (int) currentTime);
        }

        try {
            String message = playerName + ":" + action;
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
            socket.send(sendPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(receivePacket);

                    String serverMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    updatePlayerPositions(serverMessage);

                    // Vẽ lại giao diện ngay khi nhận được dữ liệu
                    racePanel.repaint();
                }
            } catch (Exception e) {
                System.out.println("Connection lost: " + e.getMessage());
                JOptionPane.showMessageDialog(frame, "Connection to the server has been lost.");
                System.exit(0); // Đóng chương trình khi mất kết nối
            }
        }).start();
    }

    private boolean hasWinner = false; // Cờ kiểm tra đã có người thắng

    private void updatePlayerPositions(String serverMessage) {
        String[] lines = serverMessage.split("\n");
        boolean someoneWon = false;

        for (String line : lines) {
            if (line.startsWith("Race Status")) {
                continue;
            }
            if (line.equals("Race has been reset!")) {
                playerPositions.clear();
                racePanel.repaint(); // Vẽ lại giao diện ngay khi reset
                return; // Kết thúc xử lý
            }

            // Kiểm tra xem có người chơi mới không
            if (line.startsWith("NEW_PLAYER:")) {
                String newPlayerName = line.substring("NEW_PLAYER:".length()).trim();
                System.out.println("Hello, " + newPlayerName);
                playerPositions.putIfAbsent(newPlayerName, 0); // Thêm người chơi mới vào danh sách
                racePanel.repaint(); // Cập nhật lại giao diện khi có người chơi mới
                continue; // Bỏ qua việc xử lý thông tin về vị trí của người chơi mới
            }

            // Kiểm tra người chơi rời game
            if (line.startsWith("PLAYER_LEFT:")) {
                String playerNameLeft = line.substring("PLAYER_LEFT:".length()).trim();
                playerPositions.remove(playerNameLeft); // Xóa người chơi khỏi danh sách
                racePanel.repaint(); // Vẽ lại giao diện khi có người chơi rời
                continue;
            }

            String[] parts = line.split(":");
            if (parts.length == 2) {
                String player = parts[0].trim();
                int position = Integer.parseInt(parts[1].trim());
                playerPositions.put(player, position);

                // Kiểm tra nếu xe đạt hoặc vượt qua vạch đích
                if (position >= FINISH_LINE) {
                    someoneWon = true;
                }
            }
        }

        racePanel.repaint(); // Cập nhật lại giao diện

        if (someoneWon) {
            StringBuilder winnerList = new StringBuilder("Top Finishers:\n");
            playerPositions.entrySet()
                    .stream()
                    .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                    .limit(3) // Chỉ lấy Top 3
                    .forEach(e -> winnerList.append(e.getKey()).append(": ").append(e.getValue()).append("\n"));

            JOptionPane.showMessageDialog(frame, winnerList.toString());
            sendAction("RESTART"); // Gửi lệnh chơi lại
        }
    }

    public static void main(String[] args) {
        String playerName = JOptionPane.showInputDialog("Enter your name:");
        if (playerName != null && !playerName.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> new GameClient(playerName.trim()));
        } else {
            System.out.println("Player name is required!");
        }
    }

    // Inner class for the race panel
    static class RacePanel extends JPanel {

        private ConcurrentHashMap<String, Integer> playerPositions;
        private String playerName;

        public RacePanel(ConcurrentHashMap<String, Integer> playerPositions, String playerName) {
            this.playerPositions = playerPositions;
            this.playerName = playerName;

            // Khởi tạo vị trí mặc định (nếu cần)
//            playerPositions.putIfAbsent(playerName, 0);
        }

        // Trong phương thức paintComponent trong RacePanel
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Vẽ lại đường đua
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(50, 50, 700, 400);

            // Vẽ các làn đường
            g.setColor(Color.WHITE);
            for (int i = 1; i <= 4; i++) {
                int laneY = 50 + i * 80;
                g.drawLine(50, laneY, 750, laneY);
            }

            // Vẽ vạch đích
            g.setColor(Color.YELLOW);
            g.fillRect(FINISH_LINE + 100, 50, 5, 400); // Vẽ vạch đích dọc

            // Vẽ các xe
            int lane = 1;
            for (String player : playerPositions.keySet()) {
                int x = 50 + playerPositions.get(player);
                int y = 50 + lane * 80 - 40;

                // Vẽ hình xe
                if (player.equals(playerName)) {
                    g.setColor(Color.RED); // Xe của mình
                } else {
                    g.setColor(Color.BLUE); // Xe của người khác
                }

                // Vẽ thân xe (hình chữ nhật)
                g.fillRect(x, y, 60, 30); // Chiều rộng 60 và chiều cao 30 cho thân xe

                // Vẽ bánh xe (hình tròn)
                g.setColor(Color.BLACK);
                g.fillOval(x + 5, y + 25, 10, 10); // Bánh xe trái
                g.fillOval(x + 45, y + 25, 10, 10); // Bánh xe phải

                // Vẽ tên người chơi
                g.setColor(Color.BLACK);
                g.drawString(player, x, y - 5); // Ghi tên người chơi

                // Hiển thị thời gian hồi chiêu nếu có (Trên đỉnh đầu xe)
                if (player.equals(playerName)) {
                    Integer lastUsedTime = playerCooldowns.get(playerName);
                    if (lastUsedTime != null) {
                        long currentTime = System.currentTimeMillis();
                        long cooldownRemaining = BOOST_COOLDOWN - (currentTime - lastUsedTime);

                        // Nếu còn thời gian hồi chiêu
                        if (cooldownRemaining > 0) {
                            g.setColor(Color.RED);
                            g.drawString("BOOST: " + (cooldownRemaining / 1000) + "s", x + 20, y - 10); // Hiển thị thời gian còn lại trên đỉnh xe
                        } // Nếu Boost đã sẵn sàng
                        else {
                            g.setColor(Color.GREEN);
                            g.drawString("BOOST: READY", x, y - 20); // Hiển thị "READY" nếu Boost có thể sử dụng
                        }
                    }
                }
                lane++;
            }
        }

    }
}
