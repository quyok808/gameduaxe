import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameClient {
    private static final String SERVER_ADDRESS = "192.168.48.176";
    private static final int SERVER_PORT = 9876;
    private static final int FINISH_LINE = 700; // Vạch đích


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
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyPressed(KeyEvent e) {
                String action = switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP -> "MOVE_FORWARD";
                    case KeyEvent.VK_DOWN -> "MOVE_BACKWARD";
                    case KeyEvent.VK_LEFT -> "TURN_LEFT";
                    case KeyEvent.VK_RIGHT -> "TURN_RIGHT";
                    default -> null;
                };
                if (action != null) {
                    sendAction(action);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {}
        });

        frame.setVisible(true);
        startListening();
    }

    private void sendAction(String action) {
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
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updatePlayerPositions(String serverMessage) {
        String[] lines = serverMessage.split("\n");
        for (String line : lines) {
            if (line.startsWith("Race Status")) continue;
            String[] parts = line.split(":");
            if (parts.length == 2) {
                String player = parts[0].trim();
                int position = Integer.parseInt(parts[1].trim());
                playerPositions.put(player, position);

                // Kiểm tra nếu xe đạt hoặc vượt qua vạch đích
                if (position >= FINISH_LINE) {
                    JOptionPane.showMessageDialog(frame, "Player " + player + " wins the race!");
                    playerPositions.put(player, 0);
                }
            }
        }
        racePanel.repaint();
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
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Vẽ đường đua
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
            g.fillRect(FINISH_LINE, 50, 5, 400); // Vẽ vạch đích dọc
            
            // Vẽ xe của người chơi
            int lane = 1;
            for (String player : playerPositions.keySet()) {
                int x = 50 + playerPositions.get(player);
                int y = 50 + lane * 80 - 40;

                if (player.equals(playerName)) {
                    g.setColor(Color.RED); // Xe của mình
                } else {
                    g.setColor(Color.BLUE); // Xe của người khác
                }

                g.fillRect(x, y, 50, 30); // Vẽ hình chữ nhật làm xe
                g.setColor(Color.BLACK);
                g.drawString(player, x, y - 5); // Ghi tên người chơi

                lane++;
            }
        }
    }
}
