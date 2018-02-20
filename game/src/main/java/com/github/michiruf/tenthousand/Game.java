package com.github.michiruf.tenthousand;

import com.github.michiruf.tenthousand.exception.GameException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Michael Ruf
 * @since 2017-12-27
 */
class Game {

    private Player[] players;
    private int currentRound;
    private RoundAdoptionState previousRoundAdoptionState;

    Game(Player[] players) {
        this.players = players;
        currentRound = 0;
        previousRoundAdoptionState = RoundAdoptionState.NO_ADOPTION;
    }

    void runGame() {
        // Delegate the game start
        for (Player player : players) {
            player.decisionInterface.onGameStart(players, player);
        }

        // Start new rounds as long as no one has won
        while (!hasAPlayerWon() || currentRound % players.length != 0) {
            Player player = getCurrentRoundPlayer();
            try {
                startPlayerTurn(player);
            } catch (GameException e) {
                // Delegate exceptions
                player.decisionInterface.onError(e);
            }
            currentRound++;
        }

        // Delegate the game end
        for (Player player : players) {
            player.decisionInterface.onGameEnd(players, getWonPlayers());
        }
    }

    Player[] getWonPlayers() {
        List<Player> wonPlayers = Arrays.asList(players);
        int maxPoints = wonPlayers.stream()
                .filter(player -> player.getPoints() >= Configuration.WON_GAME_THRESHOLD)
                .mapToInt(Player::getPoints)
                .max()
                .orElse(-1);
        wonPlayers = wonPlayers.stream()
                .filter(player -> player.getPoints() == maxPoints)
                .collect(Collectors.toList());
        return wonPlayers.toArray(new Player[wonPlayers.size()]);
    }

    private void startPlayerTurn(Player player) throws GameException {
        // TODO Separate into Round class and apply only decisions through this method

        // Initial values
        int points = 0;
        int numberOfDices = 0;
        boolean failed = false;
        boolean adopted = false;

        // Ask the player to adopt or not
        AdoptAction adoptAction = player.decisionInterface.onTurnStart(previousRoundAdoptionState);

        // Do the adoption change if possible
        if (adoptAction == AdoptAction.ADOPT) {
            if (!previousRoundAdoptionState.isAdoptionAvailable()) {
                throw new GameException("Adoption only possible if the previous player got points", player);
            }
            if (previousRoundAdoptionState.adoptedPoints > player.getPoints()) {
                throw new GameException("Adoption only possible if enough points", player);
            }
            points = previousRoundAdoptionState.adoptedPoints;
            numberOfDices = previousRoundAdoptionState.adoptedNumberOfDicesRemaining;
            adopted = true;
        }

        GameException exception = null;
        try {
            DiceAction diceAction;
            do {
                // If there are no dices left. Put all back in ("Alle wieder nei")
                if (numberOfDices == 0) {
                    numberOfDices = Configuration.NO_DICES;
                }

                // Roll the dice
                Dice[] dices = Dice.randomSorted(numberOfDices);

                // If there was no new value, the players turn is over
                if (!DicesValueDetector.hasAValue(dices)) {
                    failed = true;
                    break;
                }

                // Only inform the player when dices are available
                diceAction = player.decisionInterface.onTurnDiceRolled(dices, points);

                // At least one dice needs to be kept
                if (diceAction.dicesToKeep.length == 0) {
                    throw new GameException("At least one dice must be kept", player);
                }

                // If there was were dices that do not contain any value, the player failed
                if (!DicesValueDetector.hasOnlyValues(diceAction.dicesToKeep)) {
                    throw new GameException("Not all dices to keep were worth something", player);
                }

                // If there are dices that were not rolled, the player failed
                if (!DicesValueDetector.contains(dices, diceAction.dicesToKeep)) {
                    throw new GameException("Dices that were not rolled were selected to keep", player);
                }

                // Keep the dices the player wanted (by the points)
                points += DicesValueDetector.calculatePoints(diceAction.dicesToKeep);
                numberOfDices -= diceAction.dicesToKeep.length;
            }
            // As long as the player wants to continue;
            while (diceAction.continueTurn);
        } catch (GameException e) {
            exception = e;
            failed = true;
        }

        // Check if thresholds are reached
        if (!failed) {
            if (!player.hasEnteredGame() && !player.isEnterGameThresholdReached(points)) {
                exception = new GameException(String.format("Enter game threshold not reached (%d of %d)",
                        points, Configuration.ENTER_GAME_THRESHOLD), player);
                failed = true;
            } else if (points < Configuration.ROUND_THRESHOLD) {
                exception = new GameException(String.format("Round point threshold not reached (%d of %d)",
                        points, Configuration.ROUND_THRESHOLD), player);
                failed = true;
            }
        }

        // Update the adoption state and add the points
        if (!failed) {
            player.addPoints(points);
            previousRoundAdoptionState = new RoundAdoptionState(points, numberOfDices);
            player.decisionInterface.onTurnEnd(true, points);
            return;
        }

        // Remove points if failed and set the adoption state
        int negAdoptedPoints = -previousRoundAdoptionState.adoptedPoints;
        if (adopted) {
            player.addPoints(negAdoptedPoints);
        }
        previousRoundAdoptionState = RoundAdoptionState.NO_ADOPTION;
        player.decisionInterface.onTurnEnd(false, negAdoptedPoints);

        // Redirect the exception as normally to the calling method
        // By this we ensure that adoption is applied also if a exception occurs
        if (exception != null) {
            throw exception;
        }
    }

    private boolean hasAPlayerWon() {
        boolean won = false;
        for (Player player : players) {
            won |= player.getPoints() >= Configuration.WON_GAME_THRESHOLD;
        }
        return won;
    }

    private Player getCurrentRoundPlayer() {
        return players[currentRound % players.length];
    }
}
