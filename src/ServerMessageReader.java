import java.io.*;
import java.util.*;
import java.net.*;
public class ServerMessageReader {
    private static final int port = 9999; // 서버포트
    private static Map<String, ClientThread> client = new HashMap<>(); // 연결된 클라이언트 관리
    private static Map<Integer, ChatRoom> chatRooms = new HashMap<>(); // 채팅방 관리
    private static Map<String, User> users = new HashMap<>(); // 사용자 관리
    private static int count = 0; // 채팅 번호

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Char Server Port : " + port + " 번호 접속중");
        try{
            while (true) {
               Socket socket =  serverSocket.accept(); // 클라이언트 접속 대기
               new ClientThread(socket).start(); // 클라이언트 접속 스레드 시작
            }
        }catch (IOException e){
            System.out.println("서버 시작 중 에러:" + e.getMessage());
        }finally {
            serverSocket.close(); // 서버 소켓 닫기
        }
    }

    // 사용자 정보 저장 클래스
    static class User {
        String username; // 사용자의 이름
        String password; // 사용자의 비밀번호
        int spool = 0; // 비밀번호 실패 시도 횟수
        long look = 0; // 계정 잠금 시간
        boolean login = false; // 로그인 상태 여부
        String ipAddress; // 사용자의 IP 주소

        public User(String username, String password) {
            this.username = username;
            this.password = password;
            this.ipAddress = null;
        }
        public void setLogin(boolean login){
            this.login = login; // 로그인 상태 설정
        }
        // 계정 잠금
        public boolean isLook() {
            if (look ==0)
                return false;
            return System.currentTimeMillis() - look < 300000;
        }
    }
    // 클라이언트 처리 스레드 클래스
    private static class ClientThread extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String name; // 사용자의 닉네임
        private ChatRoom currentRoom; // 현재 접속 채팅방

        public ClientThread(Socket socket) {
            this.socket = socket; // 클라이언트 소켓 초기화
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true); // 출력 스트림
                in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 입력 스트림

                // 채팅서버
                showMain();

            } catch (IOException e) {
                System.out.println(name + "Error 발생" + e.getMessage());
            } finally {
                closeConnection(); // 연결 종료
            }
        }
        // 채팅 처리 메서드
        private void handleMessages() throws IOException {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/bye")) {
                    break; // 종료
                } else if (message.startsWith("/help")) {
                    guideMessage(); // 안내 메세지
                } else if (message.startsWith("/list")) {
                    listRoom(); // 방 목록 보기
                } else if (message.startsWith("/create")) {
                    createRoom(); // 방 생성
                } else if (message.startsWith("/join")) {
                    joinRoom(message.split(" ")[1]); // 방 입장
                } else if (message.startsWith("/exit")) {
                    exitRoom(); // 방 나가기
                } else if (message.startsWith("/users")) {
                    listUser(); // 현재 접속 중인 사용자 목록
                } else if (message.startsWith("/roomusers")) {
                    listRoomUser(); // 현재 방의 사용자 목록
                } else if (message.startsWith("/whisper")) {
                    whisper(message.split(" ", 3)); // 귓속말 기능
                } else if (message.startsWith("/quit")) {
                    showMain(); // 로그아웃 -> 초기 메뉴
                } else {
                    broadcastMessage(message); // 일반 메시지 방송
                }
            }
            closeConnection(); // 연결 종료 처리
        }


        // 닉네임 설정 메서드
        private void nameRequest() throws IOException {
            out.println("닉네임 입력하세오 : ");
            name = in.readLine().trim();
            while (client.containsKey(name)) {
                out.println("이 닉네임은 이미 사용되었습니다. 다른 닉네임 입력하세요.");
                name = in.readLine().trim();
            }
            client.put(name, this);
            out.println(name + "님 환영합니다.");
            System.out.println(name + "서버에 입장했습니다.");
        }

        // 안내 메세지 메서드
        private void guideMessage() {
            out.println("명령어: 방 목록:[/list], " +
                    "새 채팅방:[/create], " +
                    "채팅방 입장:[/join (ID)], " +
                    "채팅방 종료:[/exit], " +
                    "\n접속중인 사용자 목록:[/users], " +
                    "현재 방의 사용자 목록:[/roomusers], " +
                    "귓속말:[/whisper (이름) (메세지)]" +
                    "\n채팅서버 종료 할 경우:[/bye] ,로그아웃 할 경우:[/quit] 입력하십시오." );
        }

        // 방 목록 메서드
        private void listRoom() {
            if (chatRooms.isEmpty()) {
                out.println("존재 하지 않은 방입니다. /create 사용하여 새 방을 추가하십시오.");
            } else {
                chatRooms.forEach((id, room) -> out.println("방 " + id + ": " + room.getRoomName() +
                        " (" + room.getOccupants().size() + " 사용자)"));
            }
        }

        // 새 채팅방 생성 메서드
        private void createRoom() {
            int roomId = ++count;
            ChatRoom room = new ChatRoom(roomId, "방 " + roomId); // 새 방 생성
            chatRooms.put(roomId, room); // 생성된 방을 채팅 목록 추가
            joinRoom(String.valueOf(roomId)); // 생성된 방에 입장
            out.println("방 " + roomId + "번 생성했습니다.");
        }

        // 채팅방 메서드
        private void joinRoom(String id) {
            int roomId = Integer.parseInt(id);
            if (chatRooms.containsKey(roomId)) {
                if (currentRoom != null) {
                    currentRoom.removeOccupant(this); // 기존 방에서 사용자 제거
                }
                currentRoom = chatRooms.get(roomId); // 새 방 설정
                currentRoom.addOccupant(this); // 새 방에 사용자 추가
                out.println("입장 하셨습니다. " + currentRoom.getRoomName());
            } else {
                out.println("입력하신 방 없습니다. /create 명령어 입력하여 새 방을 추가하십시오.");
            }
        }

        // 채팅방 나가기 메서드
        private void exitRoom() {
            if (currentRoom != null) {
                currentRoom.removeOccupant(this); // 방에서 사용자 삭제
                out.println(name + "나갔습니다." + currentRoom.getRoomName());
                if (currentRoom.getOccupants().isEmpty()) {
                    chatRooms.remove(currentRoom.getRoomId()); // 사용자 없을 경우 방 삭제
                    out.println("방 " + currentRoom.getRoomId() + "번 삭제되었습니다.");
                }
                currentRoom = null; // 현재 방 정보 초기화
            }
        }

        // 접속중인 사용자 목록 출력 메서드
        private void listUser() {
            client.keySet().forEach(user -> out.println(user));
        }

        // 현재 방의 사용자 목록 출력 메서드
        private void listRoomUser() {
            if (currentRoom != null) {
                currentRoom.getOccupants().forEach(client -> out.println(client.name));
            } else {
                out.println("현재 방에 없습니다.");
            }
        }
        // 귓속말 메서드
        private void whisper(String[] parts) {
            if (parts.length < 3) {
                out.println("나: /whisper [닉네임] [메세지]");
            } else {
                String targetNickname = parts[1];
                String message = parts[2];
                if (client.containsKey(targetNickname)) {
                    client.get(targetNickname).out.println("귓속말[" + name + "]님 : " + message);
                } else {
                    out.println("사용자 : " + targetNickname + " 찾을 수 없습니다.");
                }
            }
        }
        // 안내 방송
        private void broadcastMessage(String message) {
            if (currentRoom != null) {
                currentRoom.broadcastMessage(name + ": " + message);
            } else {
                out.println("현재 방에 없습니다. 메세지 보낼 방에 입장하세요.");
            }
        }
        // 연결 종료 메서드
        private void closeConnection() {
            if (name != null) {
                client.remove(name); // 클라이언트 목록에 사용자 삭제
                if (currentRoom != null) {
                    currentRoom.removeOccupant(this); // 방에서 사용자 삭제
                    if (currentRoom.getOccupants().isEmpty()) {
                        chatRooms.remove(currentRoom.getRoomId()); // 사용자 없을 경우 방 삭제
                    }
                }
            }
            try {
                socket.close(); // 소켓 종료
                in.close(); // 입력 스트림 종료
                out.close(); // 출력 스트림 종료
            } catch (IOException e) {
                System.out.println("리소스 닫는 중에 Error 발생: " + e.getMessage());
            }
            System.out.println(name + " 서버에 나갔습니다.");
        }
        // 채팅 서버 화면 메서드
        private void showMain() throws IOException {
            out.println("채팅서버에 오신 것을 환영합니다.");
            out.println("메인 메뉴");
            out.println("1번 : 로그인 | 2번 : 회원가입 | 3번 : 종료" );

            while (true) {
            String choice = in.readLine().trim(); // 사용자 선택 입력
            switch (choice) {
                case "1":
                    login(); // 로그인
                    break;
                case "2":
                    membership(); // 회원가입
                    break;
                case "3":
                        out.println("채팅서버 종료 하시겠습니까? YES / NO");
                        String confirmExit = in.readLine().trim().toUpperCase();
                        if ("YES".equals(confirmExit)) {
                            out.println("채팅서버 종료 완료");
                            System.exit(0); // 서버 종료
                        } else if ("NO".equals(confirmExit)) {
                            out.println("종료가 취소되었습니다.");
                        } else {
                            out.println("잘못 입력 하셨습니다.");
                        }
                        break;
                default:
                    out.println("잘못된 입력입니다. 다시 입력해주세요.");
                    showMain(); // 메인 메뉴
                    break;
                }
            }
        }

        // 회원가입 메서드
        private void membership() throws IOException {
            // 아이디
            String username;
            while (true) {
                out.println("아이디 입력: (취소할 경우 'quit' 입력하시오)");
                username = in.readLine().trim();
                if ("quit".equalsIgnoreCase(username)){
                    out.println("회원가입이 취소되었습니다. 로그인 화면로 돌아갑니다.");
                    showMain();
                }
                if (!users.containsKey(username)) {
                    break;
                } else {
                    out.println("중복된 아이디입니다. 다른 아이디 입력하십시오.");
                }
            }

            // 비밀번호
            String password = "";
            String confirmPassword = "";
            while (true) {
                out.println("비밀번호 입력: ");
                password = in.readLine().trim();
                out.println("비밀번호 재확인: ");
                confirmPassword = in.readLine().trim();
                if (password.equals(confirmPassword)) {
                    users.put(username, new User(username, password));
                    out.println("회원가입이 완료되었습니다. 로그인 해주세요.");
                    showMain();
                } else {
                    out.println("비밀번호가 일치하지 않습니다.");
                }
            }
        }

        // 로그인 메서드
        private void login() throws IOException {
            String username;
            User user;
            String clientIP = socket.getInetAddress().getHostAddress();

            while (true) {
                out.println("아이디 입력: (취소할 경우 'quit' 입력하시오)");
                username = in.readLine().trim();
                if ("quit".equalsIgnoreCase(username)){
                    out.println("로그인 화면으로 돌아갑니다.");
                    showMain();
                    return;
                }
                user = users.get(username);
                // 계정 잠금
                if (user != null){
                    if (user.isLook()){
                        out.println("계정이 잠겨 있습니다.");
                        continue;
                    }
                }

                // 아이피 중복 확인
                if (user != null) {
                    if (user.login) {
                        if (!user.ipAddress.equals(clientIP)) {
                            out.println("타 IP로 현재 아이디로 접속 중입니다.");
                            showMain();
                            return;
                        }
                    }
                    break;
                } else {
                    out.println("존재하지 않는 아이디입니다. 다시 입력해주세요.");
                }

            }
            while (true) {
                out.println("비밀번호 입력:");
                String password = in.readLine().trim();
                if (user.password.equals(password)) {
                    if (user.login){
                        out.println("현재 아이디 접속 중입니다.");
                        login();
                        break;
                    }
                    user.setLogin(true);
                    user.ipAddress = clientIP;
                    out.println("로그인 성공!");
                    nameRequest();
                    guideMessage();
                    handleMessages();
                    return;
                } else {
                    user.spool++;
                    out.println("비밀번호가 틀렸습니다. 시도 횟수: " + user.spool);
                    if (user.spool >= 5) {
                        user.look = System.currentTimeMillis();
                        out.println("비밀번호를 5회 이상 틀렸습니다. 계정이 잠겨있습니다.");
                        showMain();
                    }
                }
            }
        }
    }
    // 채팅방 관리 클래스
    private static class ChatRoom {
        private int roomId;  // 채팅방 고유번호
        private String roomName;  // 채팅방의 이름
        private Set<ClientThread> resident = new HashSet<ClientThread>(); // 채팅방 참여 중인 사용자 모임

        public ChatRoom(int roomId, String roomName) {
            this.roomId = roomId;  // 방 번호
            this.roomName = roomName;  // 방 이름
        }
        // 사용자 참가
        public void addOccupant(ClientThread client) {
            resident.add(client);
        }
        // 사용자 제거
        public void removeOccupant(ClientThread client) {
            resident.remove(client);
        }
        // 참가자 목록
        public Set<ClientThread> getOccupants() {
            return resident;
        }
        // 방 번호
        public int getRoomId() {
            return roomId;
        }
        // 방 이름
        public String getRoomName() {
            return roomName;
        }
        // 메시지 방송
        public void broadcastMessage(String message) {
            for (ClientThread client : resident) {
                client.out.println(message);
            }
        }
    }
}

