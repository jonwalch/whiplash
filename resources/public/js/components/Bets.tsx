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
    if (!props.passedGuessingPeriod) {
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
      <div className="bets-container">
        <p style={{textAlign: "center"}}>Current Bets:</p>
        <div style={{ display: "flex" }}>
          {bets.map((el: any) => {
            return (
              <div
                key={el[0]}
                style={{ display: "flex", flexDirection: "column" }}
              >
                <div>{el[0]}</div>
                {el[1].map((bet: any) => {
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
    <>
      {bets && renderBets()}
    </>
  );
}