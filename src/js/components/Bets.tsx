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
      method: "GET",
      mode: "same-origin",
      credentials: "omit",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      setBets(resp);
    } else {
      setBets(null)
    }
  };

  const generateBetTRs = () => {
    const yesBets = bets["true"].bets;
    const noBets = bets["false"].bets;
    const trs = [];
    const defaultTD = <td className="bets__td"></td>
    const end : number = Math.max(yesBets.length, noBets.length);

    for (let i = 0; i < end; i++) {
      let yes = defaultTD;
      let no = defaultTD;

      if (i < yesBets.length) {
        const user = yesBets[i]["user/name"];
        const betAmount = yesBets[i]["bet/amount"];
        yes =
            <td className="bets__td" key={user + betAmount + "yes"}>
              <div className="bets__td__flex">
                <span>{user}</span>
                <span>{"$" + betAmount}</span>
              </div>
            </td>
      }

      if (i < noBets.length){
        const user = noBets[i]["user/name"];
        const betAmount = noBets[i]["bet/amount"];
        no =
            <td className="bets__td" key={user + betAmount + "no"}>
              <div className="bets__td__flex">
                <span>{user}</span>
                <span>{"$" + betAmount}</span>
              </div>
            </td>
      }

      trs.push(
          <tr className="bets__tr">
            {yes}
            {no}
          </tr>
      )
    }
    return trs;
  };

  const renderTBody = () => {
    const t = bets["true"];
    const f = bets["false"];

    return (
        <tbody>
        <tr className="bets__tr bets__team" key="Yes">
          <th className="bets__th">
            <div className="bets__th__flex">
              <span>Yes</span>
              <span>{t.odds.toFixed(2)}</span>
              <span>{"$" + t.total}</span>
            </div>
          </th>
          <th className="bets__th">
            <div className="bets__th__flex">
              <span>No</span>
              <span>{f.odds.toFixed(2)}</span>
              <span>{"$" + f.total}</span>
            </div>
          </th>
        </tr>
        </tbody>
    );
  };

  const renderBets = () => {
    return (
        <div className="bets">
          <div className="container">
            <header className="bets__header">
              <h2 className="bets__title">Current Bets</h2>
            </header>
            <table className="bets__table">
              {renderTBody()}
            </table>
            <div className="bets__innertable">
              <table className="bets__table">
                <tbody>
                {generateBetTRs()}
                </tbody>
              </table>
            </div>
          </div>
        </div>
    );
  };

  return (
    <>
      {bets && Object.keys(bets).length !== 0 && bets.constructor === Object &&
      renderBets()}
    </>
  );
}