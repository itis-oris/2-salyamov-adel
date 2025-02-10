package org.example.demo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizServer {
    private static final int PORT = 12345;
    private static Map<String, List<Question>> questionsByCategory;
    private static int currentQuestionIndex = 0;
    private static int player1Score = 0;
    private static int player2Score = 0;
    private static volatile String player1Answer = null;
    private static volatile String player2Answer = null;
    private static volatile String player1Name = null;
    private static volatile String player2Name = null;

    private static String selectedCategory = "";

    public static void main(String[] args) {
        loadQuestions();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);

            Socket clientSocket = serverSocket.accept();
            System.out.println("Player 1 connected");
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String player1In = in.readLine();

            if (player1In.split(" ").length > 1) {
                player1Name = player1In.split(" ")[0];
                selectedCategory = player1In.split(" ")[1];
            } else {
                player1Name = player1In;
            }

            System.out.println("Player 1 name: " + player1Name);
            out.println("WAITING_FOR_PLAYER_2");

            Socket opponentSocket = serverSocket.accept();
            System.out.println("Player 2 connected");
            BufferedReader opponentIn = new BufferedReader(new InputStreamReader(opponentSocket.getInputStream()));
            PrintWriter opponentOut = new PrintWriter(opponentSocket.getOutputStream(), true);

            String player2In = opponentIn.readLine();

            System.out.println(player2In);

            if (player2In.split(" ").length > 1) {
                player2Name = player2In.split(" ")[0];
                selectedCategory = player2In.split(" ")[1];
            } else {
                player2Name = player2In;
            }

            System.out.println("Player 2 name: " + player2Name);

            List<Question> questions = questionsByCategory.get(selectedCategory);

            out.println("START_GAME");
            opponentOut.println("START_GAME");

            while (currentQuestionIndex < questions.size()) {
                Question question = questions.get(currentQuestionIndex);
                player1Answer = null;
                player2Answer = null;

                out.println("QUESTION:" + question.getQuestion());
                opponentOut.println("QUESTION:" + question.getQuestion());
                out.println("RESULT:" + " ");
                opponentOut.println("RESULT:" + " ");

                long startTime = System.currentTimeMillis();
                long lastTimerUpdate = 0;
                boolean questionEnded = false;

                while (System.currentTimeMillis() - startTime < 8700 && !questionEnded) {
                    long currentTime = System.currentTimeMillis();
                    long timeLeft = 8 - (currentTime - startTime) / 1000;

                    if (currentTime - lastTimerUpdate >= 1000) {
                        out.println("TIMER:" + timeLeft);
                        opponentOut.println("TIMER:" + timeLeft);
                        lastTimerUpdate = currentTime;
                    }

                    if (in.ready()) {
                        player1Answer = in.readLine();

                        if (player1Answer != null && player1Answer.equalsIgnoreCase(question.getAnswer())) {
                            player1Score++;
                            out.println("STOP");
                            opponentOut.println("STOP");
                            out.println("RESULT:Правильно! Вы зарабатываете очко!");
                            opponentOut.println("RESULT:" + player1Name + " ответил правильно!");
                            questionEnded = true;
                        } else {
                            out.println("RESULT:Неверный ответ!");
                        }
                    }

                    if (opponentIn.ready()) {
                        player2Answer = opponentIn.readLine();

                        if (player2Answer != null && player2Answer.equalsIgnoreCase(question.getAnswer())) {
                            player2Score++;
                            out.println("STOP");
                            opponentOut.println("STOP");
                            opponentOut.println("RESULT:Правильно! Вы зарабатываете очко!");
                            out.println("RESULT:" + player2Name + " ответил правильно!");
                            questionEnded = true;
                        } else {
                            opponentOut.println("RESULT:Неверный ответ!");
                        }
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    out.println("PROGRESS:" + (currentTime - startTime));
                    opponentOut.println("PROGRESS:" + (currentTime - startTime));
                }

                if (!questionEnded) {
                    out.println("TIMER:0");
                    opponentOut.println("TIMER:0");
                    out.println("STOP");
                    opponentOut.println("STOP");
                    out.println("RESULT:Время вышло! Никто не ответил правильно.");
                    opponentOut.println("RESULT:Время вышло! Никто не ответил правильно.");
                }

                out.println("SCORE_UPDATE:" + player1Score);
                opponentOut.println("SCORE_UPDATE:" + player2Score);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                currentQuestionIndex++;
            }

            String finalScores = "FINAL_SCORE:" + player1Score + "," + player2Score + "," + player1Name + "," + player2Name;
            out.println(finalScores);
            opponentOut.println(finalScores);

            if (player1Score > player2Score) {
                out.println("RESULT:Вы выиграли!");
                opponentOut.println("RESULT:Вы проиграли!");
            } else if (player1Score < player2Score) {
                out.println("RESULT:Вы проиграли!");
                opponentOut.println("RESULT:Вы выиграли!");
            } else {
                out.println("RESULT:Ничья!");
                opponentOut.println("RESULT:Ничья!");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadQuestions() {
        String path = QuizServer.class.getClassLoader().getResource("questions.json").getPath();
        try (Reader reader = new FileReader(path)) {
            Gson gson = new Gson();
            questionsByCategory = gson.fromJson(reader, new TypeToken<Map<String, List<Question>>>() {}.getType());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Question {
        private String question;
        private String answer;

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }
    }
}