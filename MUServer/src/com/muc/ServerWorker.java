package com.muc;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;

public class ServerWorker extends Thread {

    private final Socket clientSocket;
    private final Server server;
    private String login = null;
    private OutputStream outputStream;
    private HashSet<String> topicSet = new HashSet<>();

    public ServerWorker(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            handleClientSocket();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleClientSocket() throws IOException, InterruptedException {
        InputStream inputStream = clientSocket.getInputStream();
        this.outputStream = clientSocket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ( (line = reader.readLine()) != null) {
            String tokens[] = line.split(" ");
            if (tokens.length > 0) {
                String cmd = tokens[0];
                if ("logoff".equalsIgnoreCase(cmd) || "logout".equalsIgnoreCase(cmd)) {
                    handleLogoff();
                    break;
                }
                else if ("login".equalsIgnoreCase(cmd)) {
                    handleLogin(outputStream, tokens);
                }
                else if("msg".equalsIgnoreCase(cmd)){
                    String[] tokensMsg = line.split(" ", 3);
                    handleMessage(tokensMsg);
                }
                else if("join".equalsIgnoreCase(cmd)){
                    handleJoin(tokens);
                }
                else if("leave".equalsIgnoreCase(cmd)){
                    handleLeave(tokens);
                }
                else {
                    String msg = "Unknown Command: " + cmd + "\n";
                    outputStream.write(msg.getBytes());
                }
            }
        }

        clientSocket.close();
    }

    private void handleLeave(String[] tokens) {
        if (tokens.length > 1){
            String topic = tokens[1];
            topicSet.remove(topic);
        }
    }

    public boolean topicMember(String topic){
        return topicSet.contains(topic);
    }

    private void handleJoin(String[] tokens){
        if (tokens.length > 1){
            String topic = tokens[1];
            topicSet.add(topic);
        }
    }

    private void handleMessage(String[] tokens) throws IOException{
        String receiver = tokens[1];
        String message = tokens[2];

        boolean isTopic = receiver.charAt(0) == '#';

        List<ServerWorker> workerList = server.getWorkerList();
        for(ServerWorker worker : workerList){
            if(isTopic) {
                if (worker.topicMember(login)) {

                    if (worker.topicMember(receiver)) {
                        String outbound = receiver + "(" + login + ")" + ": " + message + "\n";
                        worker.send(outbound);
                    }

                }
            }
            else if(receiver.equals(worker.getLogin())){
                String outbound = login + ": " + message + "\n";
                worker.send(outbound);
            }
        }
    }

    private void handleLogoff() throws IOException {
        server.removeWorker(this);
        List<ServerWorker> workerList = server.getWorkerList();

        String onlineMsg = "Offline: " + login + "\n";
        for(ServerWorker worker : workerList) {
            if (!login.equals(worker.getLogin())) {
                worker.send(onlineMsg);
            }
        }
        clientSocket.close();
    }

    public String getLogin() {
        return login;
    }

    private void handleLogin(OutputStream outputStream, String[] tokens) throws IOException {
        if (tokens.length == 3) {
            String login = tokens[1];
            String password = tokens[2];

            if ((login.equals("guest") && password.equals("guest")) || (login.equals("chris") && password.equals("chris")) ) {
                String msg = "Welcome!" + login + "\n";
                outputStream.write(msg.getBytes());
                this.login = login;
                System.out.println("User " + login + " logged in successfully\n");

                List<ServerWorker> workerList = server.getWorkerList();

                for(ServerWorker worker : workerList) {
                    if (worker.getLogin() != null) {
                        if (!login.equals(worker.getLogin())) {
                            String msg2 = "Online: " + worker.getLogin() + "\n";
                            send(msg2);
                        }
                    }
                }


                String onlineMsg = "Online " + login + "\n";
                for(ServerWorker worker : workerList) {
                    if (!login.equals(worker.getLogin())) {
                        worker.send(onlineMsg);
                    }
                }
            } else {
                String msg = "Incorrect Login!\n";
                outputStream.write(msg.getBytes());
            }
        }
    }

    private void send(String msg) throws IOException {
        if (login != null) {
            outputStream.write(msg.getBytes());
        }
    }
}
