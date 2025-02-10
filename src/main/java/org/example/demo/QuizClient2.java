package org.example.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class QuizClient2 extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int TOTAL_QUESTIONS = 4;

    private PrintWriter out;
    private BufferedReader in;
    private Label questionLabel;
    private Label scoreLabel;
    private Label timerLabel;
    private TextField answerField;
    private Label resultLabel;
    private ProgressBar progressBar;
    private String playerName;
    private ImageView stickmanImageView;

    private VBox root;
    private TextField nameField;
    private Button startButton;
    private Button submitButton;
    private Label namePromptLabel;

    private int player1Score = 0;
    private int player2Score = 0;
    private String player1Name = "";
    private String player2Name = "";

    @Override
    public void start(Stage primaryStage) {
        root = new VBox(20);
        root.setStyle("-fx-background-color: white; -fx-padding: 20;");

        namePromptLabel = new Label("Введите свое имя:");
        namePromptLabel.setFont(Font.font("Arial", 16));
        namePromptLabel.setStyle("-fx-text-fill: #333333;");

        nameField = new TextField();
        nameField.setPromptText("Имя");
        nameField.setStyle("-fx-font-size: 14; -fx-padding: 10;");

        startButton = new Button("Начать игру");
        startButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20;");
        startButton.setOnAction(e -> startGame());

        root.getChildren().addAll(namePromptLabel, nameField, startButton);

        questionLabel = new Label("Ждем 2 игрока");
        questionLabel.setFont(Font.font("Arial", 18));
        questionLabel.setStyle("-fx-text-fill: #333333;");

        stickmanImageView = new ImageView();
        stickmanImageView.setFitHeight(84);
        stickmanImageView.setFitWidth(52);

        scoreLabel = new Label("Счет: 0");
        scoreLabel.setFont(Font.font("Arial", 16));
        scoreLabel.setStyle("-fx-text-fill: #555555;");

        timerLabel = new Label("Осталось времени: 8");
        timerLabel.setFont(Font.font("Arial", 16));
        timerLabel.setStyle("-fx-text-fill: #555555;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setPrefHeight(20);
        progressBar.setStyle("-fx-accent: #4CAF50;");

        answerField = new TextField();
        answerField.setPromptText("Введите ответ");
        answerField.setStyle("-fx-font-size: 14; -fx-padding: 10;");

        submitButton = new Button("Отправить");
        submitButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 20;");
        submitButton.setOnAction(e -> sendAnswer());

        resultLabel = new Label();
        resultLabel.setFont(Font.font("Arial", 14));
        resultLabel.setStyle("-fx-text-fill: #0000AA;");
        resultLabel.setVisible(false);

        Scene scene = new Scene(root, 550, 450);
        primaryStage.setTitle("Игра-квиз");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startGame() {
        playerName = nameField.getText().trim();

        if (playerName.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Имя не может быть пустым");
            alert.setContentText("Пожалуйста, введите ваше имя.");
            alert.showAndWait();
            return;
        }

        root.getChildren().removeAll(namePromptLabel, nameField, startButton);

        root.getChildren().addAll(questionLabel, scoreLabel, timerLabel, stickmanImageView, progressBar, answerField, submitButton, resultLabel);

        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(playerName);

            String message = in.readLine();
            if (message.equals("WAITING_FOR_PLAYER_2")) {
                Platform.runLater(() -> {
                    questionLabel.setText("Ждем 2 игрока");
                });
            }

            while ((message = in.readLine()) != null) {
                if (message.startsWith("START_GAME")) {
                    Platform.runLater(() -> {
                        questionLabel.setText("Игра началась!");
                    });
                } else if (message.startsWith("QUESTION:")) {
                    String question = message.substring(9);
                    Platform.runLater(() -> {
                        questionLabel.setText(question);
                        progressBar.setProgress(0);
                        startStickmanGif();
                    });
                } else if (message.startsWith("SCORE_UPDATE:")) {
                    String scoreUpdate = message.substring(13);
                    Platform.runLater(() -> {
                        scoreLabel.setText("Счет: " + scoreUpdate);
                    });
                } else if (message.startsWith("TIMER:")) {
                    String timeLeft = message.substring(6);
                    Platform.runLater(() -> timerLabel.setText("Осталось времени: " + timeLeft));
                } else if (message.startsWith("RESULT:")) {
                    String result = message.substring(7);
                    Platform.runLater(() -> {
                        resultLabel.setText(result);
                        resultLabel.setVisible(true);
                    });
                } else if (message.startsWith("PROGRESS:")) {
                    String timeLeft = message.substring(9);
                    Platform.runLater(() -> {
                        double progress = Double.parseDouble(timeLeft) / 10000 * 1.16;
                        Platform.runLater(() -> updateProgress(progress));

                    });
                } else if (message.equals("STOP")) {
                    Platform.runLater(this::stopStickmanGif);
                } else if (message.startsWith("FINAL_SCORE:")) {
                    String[] scores = message.substring(12).split(",");
                    if (scores.length >= 4) {
                        player1Score = Integer.parseInt(scores[0]);
                        player2Score = Integer.parseInt(scores[1]);
                        player1Name = scores[2];
                        player2Name = scores[3];

                        Platform.runLater(this::showFinalResults);
                    } else {
                        System.err.println("Ошибка: некорректное сообщение FINAL_SCORE");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startStickmanGif() {
        stickmanImageView.setImage(new Image("/static/gif/walk.gif"));
    }

    private void stopStickmanGif() {
        stickmanImageView.setImage(new Image("/static/img/stand.png"));
    }

    private void updateProgress(double normalizedProgress) {
        progressBar.setProgress(normalizedProgress);
        double progress = normalizedProgress * progressBar.getWidth();
        stickmanImageView.setTranslateX(progress);
    }

    private void showFinalResults() {
        root.getChildren().clear();

        Label finalResultLabel = new Label();
        if (player1Score > player2Score) {
            finalResultLabel.setText(player1Name + " выиграл(а)!");
        } else if (player1Score < player2Score) {
            finalResultLabel.setText(player2Name + " выиграл(а)!");
        } else {
            finalResultLabel.setText("Ничья!");
        }
        finalResultLabel.setFont(Font.font("Arial", 24));
        finalResultLabel.setStyle("-fx-text-fill: #333333;");

        VBox chartContainer = new VBox(20);
        chartContainer.setStyle("-fx-alignment: center;");

        HBox player1Bar = createBar(player1Name, player1Score, "#4CAF50");
        HBox player2Bar = createBar(player2Name, player2Score, "#2196F3");

        chartContainer.getChildren().addAll(player1Bar, player2Bar);

        root.getChildren().addAll(finalResultLabel, chartContainer);
    }

    private HBox createBar(String playerName, int score, String color) {
        VBox barContainer = new VBox(5);
        barContainer.setStyle("-fx-alignment: center;");

        Label scoreLabel = new Label(playerName + ": " + score + "/" + TOTAL_QUESTIONS);
        scoreLabel.setFont(Font.font("Arial", 16));
        scoreLabel.setStyle("-fx-text-fill: #333333;");

        ProgressBar bar = new ProgressBar((double) score / TOTAL_QUESTIONS);
        bar.setPrefWidth(300);
        bar.setPrefHeight(20);
        bar.setStyle("-fx-accent: " + color + ";"); // Цвет полоски

        barContainer.getChildren().addAll(scoreLabel, bar);

        HBox container = new HBox();
        container.setStyle("-fx-alignment: center;");
        container.getChildren().add(barContainer);

        return container;
    }

    private void sendAnswer() {
        String answer = answerField.getText();
        if (out != null && !answer.isEmpty()) {
            out.println(answer);
            answerField.clear();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}