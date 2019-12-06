import React, { useState, useEffect } from "react";
import { useInterval } from "../common";
import { baseUrl } from "../config/const";

export function Bets(props: any) {
  const [bets, setBets] = useState<any>(null);
  
  useEffect(() => {
    if (props.matchID && props.currentGame && props.currentGame.id) {
      getBets();
    }
  }, [props.matchID, props.currentGame]);

  useInterval(() => {
    if (props.currentGame && props.currentGame.id) {
      getBets();
    }
  }, 5000);

  //show bets for current game
  const getBets = async () => {
    const url =
      baseUrl +
      "leaderboard/bets" +
      "?match_id=" +
      props.matchID +
      "&game_id=" +
      props.currentGame.id;
    const response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      setBets(Object.entries(resp));
    }
  };

  const renderBets = () => {
    return (
      <div className="container">
        <p>Current Bets:</p>
        <div>
          {bets.map((el: any) => {
            const teamName = el[0];
            const teamBets = el[1];
            return (
              <div key={teamName}>
                <div>
                  {teamName} Odds:{teamBets.odds.toFixed(2)} Total:$
                  {teamBets.total}
                </div>
                {teamBets.bets.map((bet: any) => {
                  return (
                    <div key={bet["user/name"]}>
                      <div>{bet["user/name"]}</div>
                      <div>${bet["bet/amount"]}</div>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div className="bets">
      {bets && renderBets()}
    </div>
  );
}