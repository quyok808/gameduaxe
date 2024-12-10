import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private static final int PORT = 9876;
    private static final int FINISH_LINE = 100; // Giới hạn vạch đích
    private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private static DatagramSocket serverSocket;

    public static void main(String[] args) {
        try {
            serverSocket = new DatagramSocket(PORT);
            byte[] buffer = new byte[1024];
            System.out.println("Racing Server is running...");

            while (true) {
                // Nhận dữ liệu từ máy khách
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                // Phân tích và cập nhật
                String[] parts = message.split(":");
                String playerName = parts[0];
                String action = parts[1];

                // Cập nhật trạng thái của người chơi
                players.computeIfAbsent(playerName, name -> new Player(playerName, clientAddress, clientPort))
                        .updatePosition(action);

                // Gửi trạng thái cuộc đua tới tất cả người chơi
                StringBuilder raceStatus = new StringBuilder("Race Status:\n");
                boolean raceOver = false; // Biến kiểm tra trạng thái kết thúc
                for (Player player : players.values()) {
                    raceStatus.append(player.name).append(": ").append(player.getPosition()).append("\n");

                    // Kiểm tra nếu người chơi đã chiến thắng
                    if (player.getPosition() >= FINISH_LINE && !raceOver) {
                        raceStatus.append(player.name).append(" wins the race!\n");
                        raceOver = true;
                        resetRace();  // Reset lại vòng đua nếu có người chiến thắng
                    }
                }

                // Gửi trạng thái cuộc đua tới tất cả người chơi
                byte[] response = raceStatus.toString().getBytes();
                for (Player player : players.values()) {
                    DatagramPacket responsePacket = new DatagramPacket(response, response.length, player.getAddress(), player.getPort());
                    serverSocket.send(responsePacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Player {
        private String name;
        private InetAddress address;
        private int port;
        private int position;

        public Player(String name, InetAddress address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.position = 0;
        }

        public void updatePosition(String action) {
            // Kiểm tra nếu người chơi đã đến vạch đích, và reset nếu cần
            if (position >= FINISH_LINE) {
                // Gửi thông báo người chơi đã thắng và reset vòng đua
                return;  // Không làm gì nếu đã chiến thắng
            }

            switch (action) {
                case "MOVE_FORWARD" -> position += 5;
                case "MOVE_BACKWARD" -> position -= 5;
                case "TURN_LEFT", "TURN_RIGHT" -> {} // Logic bổ sung thêm
            }
        }

        public int getPosition() {
            return position;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }
    }

    // Reset lại vòng đua
    private static void resetRace() {
        // Đặt lại vị trí của tất cả người chơi về 0 khi có người thắng cuộc
        players.forEach((name, player) -> player.position = 0);

        // Thông báo cho tất cả người chơi rằng vòng đua đã được reset
        String resetMessage = "Race has been reset! Starting a new round.";
        byte[] resetResponse = resetMessage.getBytes();
        players.forEach((name, player) -> {
            try {
                DatagramPacket resetPacket = new DatagramPacket(resetResponse, resetResponse.length, player.getAddress(), player.getPort());
                serverSocket.send(resetPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
