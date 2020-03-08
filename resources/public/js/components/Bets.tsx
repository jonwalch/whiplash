import React, {useState, useEffect, useContext} from "react";
import { useInterval } from "../common";
import { baseUrl } from "../config/const";
import {failedToFetch} from "./Home";
import {LoginContext} from "../contexts/LoginContext";

export function Bets(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [bets, setBets] = useState<any>(null);

  useEffect(() => {
      getPropBets();
  }, [loggedInState.cash]);

  // TODO: only when there's an event
  useInterval(() => {
    if (props.twitchUsername != failedToFetch) {
      getPropBets();
    }
  }, 3000);

  const getPropBets = async () => {
    const url = baseUrl + "leaderboard/prop-bets";
    const response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      setBets(Object.entries(resp));
    } else {
      setBets(null)
    }
  };

  const boolStringToDisplay = (str : string) => {
    if (str == "true") {
      return "Yes";
    } else if (str == "false") {
      return "No";
    }
    return str;
  };

  const renderBets = () => {
    return (
      <div className="bets">
        <div className="container">
          <header className="bets__header">
            <h2 className="bets__title">Current Bets</h2>
          </header>
          <table className="bets__table">
            {bets.map((el: any) => {
              const teamName = boolStringToDisplay(el[0]);
              const teamBets = el[1];
              return (
                <tbody>
                  <tr className="bets__tr bets__team" key={teamName}>
                    <th className="bets__th">{teamName}</th>
                    <th className="bets__th"><strong>Odds:</strong> {teamBets.odds.toFixed(2)}</th>
                    <th className="bets__th"><strong>Total:</strong> ${teamBets.total}</th>
                  </tr>
                  {teamBets.bets.map((bet: any) => {
                    return (
                      <tr className="bets__tr" key={bet["user/name"] + teamName}>
                        <td className="bets__td" colSpan={2}>{bet["user/name"]}</td>
                        <td className="bets__td">${bet["bet/amount"]}</td>
                      </tr>
                    );
                  })}
                </tbody>
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