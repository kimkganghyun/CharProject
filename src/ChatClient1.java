import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient1 {
    public static void main(String[] args) {
        String host = "1.1.1.1";
        int port = 9999;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner stdIn = new Scanner(System.in)) {


            Thread readThread = new Thread(() -> {
                try {
                    String fromServer;
                    while ((fromServer = in.readLine()) != null) {
                        System.out.println("채팅서버 : " + fromServer);
                        if (fromServer.equalsIgnoreCase("bye"))
                            break;
                    }
                } catch (IOException e) {
                    System.out.println("서버 연결이 종료되었습니다.");
                }
            });
            readThread.start();

            // 사용자 입력 처리
            while (readThread.isAlive()) {
                if (stdIn.hasNextLine()) {
                    String userInput = stdIn.nextLine();
                    out.println(userInput);
                }
            }
        } catch (IOException e) {
            System.out.println("연결할 호스트 :  " + host + " 포트 : " + port + "Error 발생");
            e.printStackTrace();
        }
    }
}
