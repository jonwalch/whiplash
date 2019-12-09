import React, { useState, useEffect } from "react";
import { useInterval } from "../common";
import { baseUrl } from "../config/const";

export function Bets(props: any) {
  const [bets, setBets] = useState<any>(null);
  
  useEffect(() => {
    if (props.matchID && props.currentGame && props.currentGame.id) {
      getBets();
    } else {
      setBets(null);
    }
  }, [props.matchID, props.currentGame]);

  useInterval(() => {
    if (props.currentGame && props.currentGame.id) {
      getBets();
    } else {
      setBets(null);
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
      <div className="bets">
        <div className="container">
          <header className="bets__header">
            <h2 className="bets__title">Current Bets:</h2>
          </header>
          <table className="bets__table">
            {bets.map((el: any) => {
              const teamName = el[0];
              const teamBets = el[1];
              return (
                <tr className="bets__team" key={teamName}>
                  <td>
                    {teamName} Odds:{teamBets.odds.toFixed(2)} Total: ${teamBets.total}
                    <table>
                      {teamBets.bets.map((bet: any) => {
                        return (
                          <tr className="bets__user" key={bet["user/name"]}>
                            <td>{bet["user/name"]}</td>
                            <td>${bet["bet/amount"]}</td>
                          </tr>
                        );
                      })}
                    </table>
                  </td>
                </tr>
              );
            })}
          </table>
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