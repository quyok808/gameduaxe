
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {

    private static final int PORT = 9876;
    private static final int FINISH_LINE = 605; // Vị trí để thắng cuộc
    private static ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private static List<Player> finishers = Collections.synchronizedList(new ArrayList<>()); // Người chơi đã hoàn thành
    private static long startTime; // Thời gian bắt đầu cuộc đua

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            byte[] buffer = new byte[1024];
            System.out.println("Racing Server is running...");
            startTime = System.currentTimeMillis(); // Khởi động thời gian bắt đầu

            while (true) {
                // Nhận dữ liệu từ máy khách
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received from client: " + message);

                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                // Phân tích và xử lý yêu cầu
                String[] parts = message.split(":");
                String playerName = parts[0];
                String action = parts[1];

                if (action.equals("RESTART")) {
                    // Reset lại trạng thái của các người chơi và cuộc đua
                    players.clear(); // Xóa các người chơi
                    String responseMessage = "Race has been reset!";

                    // Gửi thông báo reset đến tất cả client
                    byte[] response = responseMessage.getBytes();
                    for (Player player : players.values()) {
                        DatagramPacket responsePacket = new DatagramPacket(response, response.length, player.getAddress(), player.getPort());
                        serverSocket.send(responsePacket);
                    }
                } else if (action.equals("NEW_PLAYER")) {
                    // Gửi thông báo NEW_PLAYER đến tất cả client
                    players.putIfAbsent(playerName, new Player(playerName, clientAddress, clientPort));
                    String newPlayerMessage = "NEW_PLAYER:" + playerName;
                    System.out.println("NEW_PLAYER: " + playerName);
                    byte[] response = newPlayerMessage.getBytes();
                    for (Player p : players.values()) {
                        DatagramPacket responsePacket = new DatagramPacket(response, response.length, p.getAddress(), p.getPort());
                        serverSocket.send(responsePacket);
                    }
                } else if (action.equals("PLAYER_LEFT")) {
                    // Xóa người chơi khỏi danh sách players
                    players.remove(playerName);

                    // Gửi thông báo người chơi đã rời game đến tất cả client
                    String playerLeftMessage = "PLAYER_LEFT:" + playerName;
                    System.out.println("PLAYER_LEFT: " + playerName);
                    byte[] response = playerLeftMessage.getBytes();
                    for (Player p : players.values()) {
                        DatagramPacket responsePacket = new DatagramPacket(response, response.length, p.getAddress(), p.getPort());
                        serverSocket.send(responsePacket);
                    }
                } else {
                    // Các hành động khác như di chuyển, cập nhật vị trí người chơi...
                    players.computeIfAbsent(playerName, name -> new Player(playerName, clientAddress, clientPort))
                            .updatePosition(action);
                }

                // Kiểm tra người thắng cuộc
                checkForWinners();

                // Gửi trạng thái cuộc đua hoặc danh sách người thắng
                StringBuilder responseMessage = new StringBuilder();
                if (finishers.size() > 0) {
                    responseMessage.append("Top Finishers:\n");
                    for (int i = 0; i < finishers.size(); i++) {
                        Player finisher = finishers.get(i);
                        long finishTime = finisher.getFinishTime() - startTime;
                        responseMessage.append(i + 1).append(". ").append(finisher.getName())
                                .append(" - Time: ").append(finishTime / 1000.0).append("s\n");
                    }
                } else {
                    responseMessage.append("Race Status:\n");
                    players.forEach((name, player) -> responseMessage.append(name).append(": ")
                            .append(player.getPosition()).append("\n"));
                }

                // Sau khi xử lý `action` hoặc thêm người chơi mới
                StringBuilder raceStatus = new StringBuilder("Race Status:\n");
                players.forEach((name, player) -> raceStatus.append(name).append(": ").append(player.getPosition()).append("\n"));

                // Gửi trạng thái cuộc đua đến tất cả người chơi
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

    private static void checkForWinners() {
        players.values().forEach(player -> {
            if (player.getPosition() >= FINISH_LINE && !finishers.contains(player)) {
                player.setFinishTime(System.currentTimeMillis());
                finishers.add(player);
            }
        });
        finishers.sort(Comparator.comparingLong(Player::getFinishTime)); // Sắp xếp theo thời gian hoàn thành
    }

    private static void restartGame(DatagramSocket serverSocket) {
        players.clear();
        finishers.clear();
        startTime = System.currentTimeMillis(); // Đặt lại thời gian bắt đầu
        System.out.println("Game restarted.");
    }

    static class Player {

        private String name;
        private InetAddress address;
        private int port;
        private int position;
        private long finishTime;
        private long boostStartTime; // Thời gian bắt đầu tăng tốc
        private boolean boosting; // Trạng thái tăng tốc
        private long boostCooldownEnd; // Thời gian kết thúc hồi chiêu

        public Player(String name, InetAddress address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.position = 0;
            this.finishTime = 0;
        }

        public Player(String name, InetAddress address, int port, int position, long finishTime, long boostStartTime, boolean boosting, long boostCooldownEnd) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.position = 0;
            this.finishTime = 0;
            this.boostStartTime = 0;
            this.boosting = false;
            this.boostCooldownEnd = 0;
        }

        public void updatePosition(String action) {
            long currentTime = System.currentTimeMillis();

            // Kiểm tra xem người chơi có thể tăng tốc hay không
            if (action.equals("BOOST") && canBoost(currentTime)) {
                boosting = true;
                boostStartTime = currentTime;
            }

            // Xử lý các hành động khác
            switch (action) {
                case "MOVE_FORWARD":
                    if (boosting) {
                        // Tăng tốc trong 1,5 giây
                        if (currentTime - boostStartTime <= 1500) {
                            position += 5; // Di chuyển nhanh hơn khi tăng tốc
                        } else {
                            boosting = false;
                            boostCooldownEnd = currentTime + 5000; // Đặt thời gian hồi chiêu 5 giây
                        }
                    } else {
                        position += 1; // Di chuyển bình thường
                    }
                    break;
                case "MOVE_BACKWARD":
                    if (boosting) {
                        // Nếu đang tăng tốc, giảm tốc độ lại sau một thời gian
                        if (currentTime - boostStartTime <= 1500) {
                            position -= 1; // Di chuyển lùi bình thường khi tăng tốc
                        } else {
                            boosting = false;
                            boostCooldownEnd = currentTime + 5000; // Đặt thời gian hồi chiêu 5 giây
                        }
                    } else {
                        position -= 1; // Di chuyển lùi bình thường
                    }
                    break;

                case "TURN_LEFT":
                    break;
                case "TURN_RIGHT":
                    break;
            }

            // Nếu không phải đang tăng tốc, người chơi sẽ di chuyển bình thường
            if (!boosting && currentTime >= boostCooldownEnd) {
                position += 1; // Di chuyển bình thường
            }
        }

        private boolean canBoost(long currentTime) {
            // Kiểm tra xem người chơi có thể tăng tốc hay không
            return currentTime >= boostCooldownEnd;
        }

        public int getPosition() {
            return position;
        }

        public void setFinishTime(long finishTime) {
            this.finishTime = finishTime;
        }

        public long getFinishTime() {
            return finishTime;
        }

        public String getName() {
            return name;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }
    }
}
