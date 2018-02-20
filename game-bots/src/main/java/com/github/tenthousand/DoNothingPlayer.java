package com.github.tenthousand;

import com.github.michiruf.tenthousand.*;
import com.github.michiruf.tenthousand.exception.GameException;

/**
 * @author Michael Ruf
 * @since 2018-02-18
 */
public class DoNothingPlayer implements PlayerInterface, PlayerDecisionInterface {

    @Override
    public void onInitialization() {
        // Do nothing
    }

    @Override
    public PlayerDecisionInterface getDecisionInterface() {
        return this;
    }

    @Override
    public void onGameStart(Player[] players, Player self) {
        // Do nothing
    }

    @Override
    public AdoptAction onTurnStart(RoundAdoptionState roundAdoptionState) {
        return AdoptAction.IGNORE;
    }

    @Override
    public DiceAction onTurnDiceRolled(Dice[] newDices, int pointsThisRoundSoFar) {
        // Always continuing lets this bot fail every round
        // Note that this causes the player to return invalid dices in most cases
        return new DiceAction(newDices, true);
    }

    @Override
    public void onTurnEnd(boolean successfulRound, int pointsReceived) {
        // Do nothing
    }

    @Override
    public void onGameEnd(Player[] players, Player[] wonPlayers) {
        // Do nothing
    }

    @Override
    public void onError(GameException e) {
        // Do not print errors because this player will do stuff that is not wanted by the game
    }
}
