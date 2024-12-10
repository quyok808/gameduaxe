import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private static final int PORT = 9876;
    private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
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
                players.forEach((name, player) -> raceStatus.append(name).append(": ").append(player.getPosition()).append("\n"));

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
            switch (action) {
                case "MOVE_FORWARD" -> position += 10;
                case "MOVE_BACKWARD" -> position -= 5;
                case "TURN_LEFT", "TURN_RIGHT" -> {} // Logic có thể bổ sung thêm
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
}
