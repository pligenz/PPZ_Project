import org.jetbrains.annotations.Contract;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.Socket;
import java.sql.ResultSet;
import java.util.HashMap;

/**
 * Thread (player) handing class
 * Created by PD on 05.04.2017.
 */
public class Player extends Thread {

    private Socket clientSocket;
    private DatabaseCommunicator databaseCommunicator;
    private PlayerManager playerManager;
    private LobbyManager lobbyManager;
    private Integer lobby;
    private MessageProvider messageProvider;
    private EncryptionProvider encryptionProvider;
    private int playerId = 0;
    private String playerName = "not logged";

    Player(Socket socket, PlayerManager playerManager, LobbyManager lobbyManager, DatabaseCommunicator databaseCommunicator) {
        this.clientSocket = socket;
        this.playerManager = playerManager;
        this.lobbyManager = lobbyManager;
        this.databaseCommunicator = databaseCommunicator;
        this.messageProvider = new MessageProvider(socket);
        this.encryptionProvider = new EncryptionProvider();
    }

    /**
     * TODO
     * Main Player function.
     */
    public void run() {
        while (true) {
            JSONObject message = messageProvider.getMessage();
            if (message != null) {
                String header = String.valueOf(message.get("header"));
                if (header.equalsIgnoreCase("endcon")) {
                    disconnect();
                    return;
                } else if (header.equalsIgnoreCase("login")) {
                    handleLogin(message);
                } else if (header.equalsIgnoreCase("register")) {
                    handleRegister(message);
                } else if (header.equalsIgnoreCase("newpass")) {
                    handleNewPass(message);
                } else if (header.equalsIgnoreCase("changepass")) {
                    handleChangePass(message);
                } else if (header.equalsIgnoreCase("lcreate")) {
                    handleLCreate();
                } else if (header.equalsIgnoreCase("ljoin")) {
                    handleLJoin(message);
                } else if (header.equalsIgnoreCase("lleave")) {
                    handleLLeave();
                }
            } else {
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

    MessageProvider getMessageProvider() {
        return messageProvider;
    }

    /**
     * Disconnect management
     */
    private void disconnect() {
        playerManager.disconnectPlayer(this);
        try {
            clientSocket.close();
            Debug.Log("User " + getPlayerName() + " (" + getPlayerId() + ") logged off");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Debug.Log("Exception in Player/disconnect: " + e);
        }
    }

    // DB management

    /**
     * Checks for user in the database, compares passwords
     * Sends appropriate message to the client afterwards
     *
     * @param message String message which method uses
     */
    private void handleLogin(JSONObject message) {
        HashMap<String, Object> response = new HashMap<>();
        try {
            String uName = String.valueOf(message.get("uname"));
            String uPasswd = encryptionProvider.encrypt(String.valueOf(message.get("upass")));



            String query = "SELECT * FROM accounts WHERE nickname = '" + uName + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                response.put("login", false);
                response.put("alert", "Wrong username!");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }
            for (Player player: playerManager.getPlayerList()) {
                if (player.getName().equals(uName)) {
                    response.put("login", false);
                    response.put("alert", "Already logged in!");
                    messageProvider.sendMessage(new JSONObject(response));
                }
            }
            resultSet.next();
            if (resultSet.getString("password").equals(uPasswd)) {
                response.put("login", true);
                messageProvider.sendMessage(new JSONObject(response));
                Debug.Log("User " + uName + " (" + resultSet.getInt("user_id") + ") logged in");
                setPlayerName(uName);
                setPlayerId(resultSet.getInt("user_id"));
            } else {
                response.put("login", false);
                response.put("alert", "Wrong password!");
                messageProvider.sendMessage(new JSONObject(response));
                Debug.Log("User " + uName + " (" + resultSet.getInt("user_id") + ") failed to login \nReason: Wrong Password.");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            response.put("login", false);
            response.put("alert", "Internal error");
            messageProvider.sendMessage(new JSONObject(response));
        } catch (Exception e) {
            response.put("login", false);
            response.put("alert", String.valueOf(e));
            messageProvider.sendMessage(new JSONObject(response));
        }
    }

    /**
     * Handles user registration process
     * Sends appropriate message to the client afterwards
     *
     * @param message String message which method uses
     */
    private void handleRegister(JSONObject message) {
        HashMap<String, Object> response = new HashMap<>();

        try {
            String uName = String.valueOf(message.get("uname"));
            String uPasswd = encryptionProvider.encrypt(String.valueOf(message.get("upass")));
            String backupPin = String.valueOf(message.get("backupPin"));
            String email = String.valueOf(message.get("email"));

            //Check if email already registered
            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (resultSet.isBeforeFirst()) {
                response.put("register", false);
                response.put("alert", "e-mail already registered!");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }

            //Check for username availability
            query = "SELECT * FROM accounts WHERE nickname = '" + uName + "'";
            resultSet = databaseCommunicator.executeQuery(query);
            if (resultSet.isBeforeFirst()) {
                response.put("register", false);
                response.put("alert", "Username taken");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }

            //Register user
            //Get new user id
            query = "SELECT user_id FROM accounts WHERE user_id = (SELECT max(user_id) FROM accounts)";
            resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                response.put("register", false);
                response.put("alert", "Internal database error");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }
            //Insert user info into table
            resultSet.next();
            int userID = resultSet.getInt("user_id");
            userID++;
            query = "INSERT INTO accounts (user_id, nickname, password, backup_code, email) VALUES (" +
                    userID + ",'" + uName + "','" + uPasswd + "','" + backupPin + "','" + email + "')";
            databaseCommunicator.executeUpdate(query);
            response.put("register", true);
            messageProvider.sendMessage(new JSONObject(response));

        } catch (Exception e) {
            response.put("register", false);
            response.put("alert", String.valueOf(e));
            messageProvider.sendMessage(new JSONObject(response));
        }
    }

    private void handleNewPass(JSONObject message) {
        HashMap<String, Object> response = new HashMap<>();
        try {
            String uPasswd = encryptionProvider.encrypt(String.valueOf(message.get("upass")));
            String backupPin = String.valueOf(message.get("backupPin"));
            String email = String.valueOf(message.get("email"));

            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                response.put("newPass", false);
                response.put("alert", "e-mail not found!");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }
            if (resultSet.getString("backup_code").equals(backupPin)) {
                query = "UPDATE accounts SET password='" + uPasswd + "' WHERE email='" + email + "'";
                databaseCommunicator.executeUpdate(query);
                response.put("newPass", true);
                response.put("alert", "e-mail not found!");
                messageProvider.sendMessage(new JSONObject(response));
            } else {
                response.put("newPass", false);
                response.put("alert", "Wrong code!");
                messageProvider.sendMessage(new JSONObject(response));
            }
        } catch (Exception e) {
            response.put("newPass", false);
            response.put("alert", String.valueOf(e));
            messageProvider.sendMessage(new JSONObject(response));
        }
    }

    private void handleChangePass(JSONObject message) {
        HashMap<String, Object> response = new HashMap<>();
        try {
            String oldPassword = encryptionProvider.encrypt(String.valueOf(message.get("oldpass")));
            String newPassword = encryptionProvider.encrypt(String.valueOf(message.get("newpass")));
            String email = String.valueOf(message.get("email"));

            String query = "SELECT * FROM accounts WHERE email = '" + email + "'";
            ResultSet resultSet = databaseCommunicator.executeQuery(query);
            if (!resultSet.isBeforeFirst()) {
                response.put("changepass", false);
                response.put("alert", "e-mail not found!");
                messageProvider.sendMessage(new JSONObject(response));
                return;
            }
            if (resultSet.getString("password").equals(oldPassword)) {
                query = "UPDATE accounts SET password='" + newPassword + "' WHERE email='" + email + "'";
                databaseCommunicator.executeUpdate(query);
                response.put("changepass", true);
                messageProvider.sendMessage(new JSONObject(response));
            } else {
                response.put("changepass", false);
                response.put("alert", "Wrong password!");
                messageProvider.sendMessage(new JSONObject(response));
            }
        } catch (Exception e) {
            response.put("changepass", false);
            response.put("alert", String.valueOf(e));
            messageProvider.sendMessage(new JSONObject(response));
        }
    }

    private void handleLCreate() {
        HashMap<String, Integer> response = new HashMap<>();
        setLobby(lobbyManager.createLobby(this));
        response.put("lcreate", lobbyManager.getLobbyEnterCode(getLobby()));
        messageProvider.sendMessage(new JSONObject(response));
    }

    private void handleLJoin(JSONObject message) {
        HashMap<String, Object> response = new HashMap<>();
        Integer enterCode = Integer.valueOf((String) message.get("enterCode"));
        if (lobbyManager.addToLobby(enterCode, this)) {
            response.put("ljoin", true);
        } else {
            response.put("ljoin", false);
            response.put("alert", "Wrong code!");
        }
        messageProvider.sendMessage(new JSONObject(response));
    }

    private void handleLLeave() {
        HashMap<String, Object> response = new HashMap<>();
        lobbyManager.removeFromLobby(lobby, this);
        setLobby(null);
        response.put("lleave", true);
        messageProvider.sendMessage(new JSONObject(response));
    }

    //Lobby management

    void setLobby(Integer lobby) {
        this.lobby = lobby;
    }

    @Contract(pure = true)
    private Integer getLobby() {
        return lobby;
    }

    void updatePlayers(HashMap<String, Object> message) {
        messageProvider.sendMessage(new JSONObject(message));
    }

    // Team management
    private void joinTeam(int team) {
        lobbyManager.getLobbyById(lobby).getTeamManager().addPlayerToTeam(this, team);
    }

    private void leaveTeam() {
        lobbyManager.getLobbyById(lobby).getTeamManager().removePlayer(this);
    }

    private void updateTeamPlayers() {

    }

    private void beginGame() {
        Lobby lobbyInstance = lobbyManager.getLobbyById(lobby);
        if (lobbyInstance.getInitiator() == this)
            lobbyInstance.beginGame();
    }
}