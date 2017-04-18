import java.io.IOException;
import java.net.Socket;
import java.sql.ResultSet;
import java.util.ArrayList;

/**
 * Thread (player) handing class
 * Created by PD on 05.04.2017.
 */
public class Player extends Thread {

    private Socket clientSocket;
    private DatabaseCommunicator databaseCommunicator;
    private Server up;
    private Integer lobby;
    private MessageProvider messageProvider;
    private EncryptionProvider encryptionProvider;
    private int playerId = 0;
    private String playerName = "not logged";

    /**
     * Class constructor
     *
     * @param clientSocket         Socket to be handled.
     * @param databaseCommunicator DatabaseCommunicator to be used.
     * @throws IOException when there's no data incomming.
     */
    Player(Socket clientSocket, DatabaseCommunicator databaseCommunicator, Server up) throws IOException {
        this.databaseCommunicator = databaseCommunicator;
        this.up = up;
        messageProvider = new MessageProvider(clientSocket);
        encryptionProvider = new EncryptionProvider();
        this.clientSocket = clientSocket;
    }

    /**
     * TODO
     * Main Player function.
     */
    public void run() {
        while (true) {
            Message message = messageProvider.getMessage();
            if (message != null) {
                String header = message.getHeader();
                if (header.equalsIgnoreCase("endcon")) {
                    disconnect();
                    return;
                } else if (header.equalsIgnoreCase("login")) {
                    handleLogin(message);
                } else if (header.equalsIgnoreCase("register")) {
                    handleRegister(message);
                } else if (header.equalsIgnoreCase("newpass")){
                    handleNewPass(message);
                } else if (header.equalsIgnoreCase("changepass")){
                    handleChangePass(message);
                } else if (header.equalsIgnoreCase("lcreate")) {
                    handleLCreate();
                } else if (header.equalsIgnoreCase("ljoin")) {
                    handleLJoin(message);
                } else if (header.equalsIgnoreCase("lleave")) {
                    handleLLeave();
                }
            }
            else {
                disconnect();
                break;
            }
        }
    }

    // Player management

    /**
     * @return DB playerID
     */
    int getPlayerId() {
        return playerId;
    }

    /**
     * @param playerId value got from DB
     */
    private void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    /**
     * @return DB nickname
     */
    String getPlayerName() {
        return playerName;
    }

    /**
     * @param playerName value got from DB
     */
    private void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    /**
     *
     */
    private void disconnect() {
            up.disconnectPlayer(this);
        try {
            clientSocket.close();
            System.out.println("User " + getPlayerName() + " (" + getPlayerId() + ") logged off");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.out.println("Exception in Player/disconnect: " + e);
        }
    }

    // DB management

    /**
     * Checks for user in the database, compares passwords
     * Sends appropriate message to the client afterwards
     *
     * @param message String message which method uses
     */
    private void handleLogin(Message message) {
        try {
            String splitted[] = message.getStringMultipleContent();
            String uName = splitted[0];
            String uPasswd = encryptionProvider.encrypt(splitted[1]);

            String query = "SELECT * FROM accounts WHERE nickname = '" + uName + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "Wrong username!"));
                return;
            }
            resultSet.next();
            if (resultSet.getString("password").equals(uPasswd)) {
                messageProvider.sendMessage(new Message("login", true));
                System.out.println("User " + uName + " (" + resultSet.getInt("user_id") + ") logged in");
                setPlayerName(uName);
                setPlayerId(resultSet.getInt("user_id"));
            } else {
                messageProvider.sendMessage(new Message("alert", "Wrong password!"));
                System.out.println("User " + uName + " (" + resultSet.getInt("user_id") + ") failed to login \nReason: Wrong Password.");
            }
        } catch (ArrayIndexOutOfBoundsException e){
            messageProvider.sendMessage(new Message("alert", "Internal error"));
        } catch (Exception e) {
            messageProvider.sendMessage(new Message("alert", String.valueOf(e)));
        }
    }

    /**
     * Handles user registration process
     * Sends appropriate message to the client afterwards
     *
     * @param message String message which method uses
     */
    private void handleRegister(Message message) {
        try {
            String splitted[] = message.getStringMultipleContent();
            String uName = splitted[0];
            String uPasswd = encryptionProvider.encrypt(splitted[1]);
            String backupPin = splitted[2];
            String email = splitted[3];

            //Check if email already registered
            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "e-mail already registered!"));
                return;
            }

            //Check for username availability
            query = "SELECT * FROM accounts WHERE nickname = '" + uName + "'";
            resultSet = databaseCommunicator.executeQuery(query);
            if (resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "Username taken!"));
                return;
            }

            //Register user
            //Get new user id
            query = "SELECT user_id FROM accounts WHERE user_id = (SELECT max(user_id) FROM accounts)";
            resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "Internal database error"));
                return;
            }
            //Insert user info into table
            resultSet.next();
            int userID = resultSet.getInt("user_id");
            userID++;
            query = "INSERT INTO accounts (user_id, nickname, password, backup_code, email) VALUES (" +
                    userID + ",'" + uName + "','" + uPasswd + "','" + backupPin + "','" + email + "')";
            databaseCommunicator.executeUpdate(query);
            messageProvider.sendMessage(new Message("register", true));

        } catch (Exception e) {
            messageProvider.sendMessage(new Message("alert", String.valueOf(e)));
        }
    }

    private void handleNewPass(Message message) {
        try {
            String splitted[] = message.getStringMultipleContent();
            String email = splitted[0];
            String uPasswd = encryptionProvider.encrypt(splitted[1]);
            String backupPin = splitted[2];

            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "e-mail not found!"));
                return;
            }
            if (resultSet.getString("backup_code").equals(backupPin)) {
                query = "UPDATE accounts SET password='"+uPasswd+"' WHERE email='"+email+"'";
                databaseCommunicator.executeUpdate(query);
                messageProvider.sendMessage(new Message("newpass", true));
            } else {
                messageProvider.sendMessage(new Message("alert", "Wrong code!"));
            }
        } catch (Exception e) {
            messageProvider.sendMessage(new Message("alert", String.valueOf(e)));
        }
    }

    private void handleChangePass(Message message) {
        try {
            String splitted[] = message.getStringMultipleContent();
            String email = splitted[0];
            String oldPassword = encryptionProvider.encrypt(splitted[1]);
            String newPassword = encryptionProvider.encrypt(splitted[2]);

            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                messageProvider.sendMessage(new Message("alert", "e-mail not found!"));
                return;
            }
            if (resultSet.getString("password").equals(oldPassword)) {
                query = "UPDATE accounts SET password='"+newPassword+"' WHERE email='"+email+"'";
                databaseCommunicator.executeUpdate(query);
                messageProvider.sendMessage(new Message("changepass", true));
            } else {
                messageProvider.sendMessage(new Message("alert", "Wrong password!"));
            }
        } catch (Exception e) {
            messageProvider.sendMessage(new Message("alert", String.valueOf(e)));
        }
    }

    private void handleLCreate() {
        createLobby();
        int enterCode = up.getLobbyEnterCode(getLobby());
        messageProvider.sendMessage(new Message("lcreate", enterCode));
    }

    private void handleLJoin(Message message) {
        int enterCode = message.getNumberContent();
        joinLobby(enterCode);
        messageProvider.sendMessage(new Message("ljoin", true));
    }

    private void handleLLeave() {
        leaveLobby();
        messageProvider.sendMessage(new Message("lleave", true));
    }

    //Lobby management

    private void createLobby() {
        setLobby(up.createLobby(this));
    }

    private void joinLobby(int enterCode) {
        up.addToLobby(enterCode, this);
    }

    void setLobby(Integer lobby) {
        this.lobby = lobby;
    }

    private Integer getLobby() {
        return lobby;
    }

    private void leaveLobby() {
        up.removeFromLobby(lobby, this);
        setLobby(null);
    }

    void updatePlayers(ArrayList<Player> players) {
        Message message = new Message();
        message.setHeader("players");
        for (Player player : players) {
            message.addToStringContent(player.getPlayerName());
        }
        messageProvider.sendMessage(message);
    }
}
