import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import "../../css/App.css";
import { Link } from "react-router-dom";
import { baseUrl } from "../config/const"
import { useInterval } from "../common";

export interface WeeklyLeader {
  user_name: string;
  payout: number;
}

export interface Leader {
  user_name: string;
  cash: number;
}

export function Leaderboard() {
  const [weeklyLeaderboard, setWeeklyLeaderboard] = useState<WeeklyLeader[]>([]);
  const [leaderboard, setLeaderboard] = useState<Leader[]>([]);

  useEffect(() => {
    getWeeklyLeaderboard();
    getLeaderboard();
  }, []);

  //every 5 minutes
  useInterval(() => {
    getWeeklyLeaderboard();
    getLeaderboard();
  }, 300000);

  const getLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/all-time", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    setLeaderboard(resp);
  };

  const renderLeaderboard = () => {
    return (
      <table className="leaderboard__table">
        <thead>
          <tr className="leaderboard__tr">
            <th className="leaderboard__th">User</th>
            <th className="leaderboard__th">Total Whiplash Cash</th>
          </tr>
        </thead>
        <tbody>
          {leaderboard.map((leader: Leader) => {
            return (
              <tr className="leaderboard__tr" key={leader.user_name}>
                <td className="leaderboard__td">{leader.user_name}</td>
                <td className="leaderboard__td">${leader.cash}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

  const getWeeklyLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/weekly", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
//     setWeeklyLeaderboard(resp);
    setWeeklyLeaderboard([{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000},{user_name: 'carl', payout: 2000}]);
  };

  const renderWeeklyLeaderboard = () => {
    if (weeklyLeaderboard.length == 0) {
      return <p className="twitch__message">No payouts yet for this week!</p>;
    } else {
      return (
        <div className="leaderboard__table-container">
          <table className="leaderboard__table">
            <thead className="leaderboard__thead">
              <tr className="leaderboard__tr">
                <th className="leaderboard__th">User</th>
                <th className="leaderboard__th">Payout</th>
              </tr>
            </thead>
            <tbody>
              {weeklyLeaderboard.map((leader: WeeklyLeader) => {
                return (
                  <tr className="leaderboard__tr" key={leader.user_name}>
                    <td className="leaderboard__td">{leader.user_name}</td>
                    <td className="leaderboard__td">${leader.payout}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      );
    }
  };

  return (
    <div className="leaderboard">
      <div className="container leaderboard__container">
        <header className="leaderboard__header leaderboard__header--primary">
          <h2 className="leaderboard__title">Leaderboard</h2>
        </header>
        <section className="leaderboard__section">
          <header className="leaderboard__header">
            <h3 className="leaderboard__subtitle">All Time Top Ten</h3>
          </header>
          {renderLeaderboard()}
        </section>
        <section className="leaderboard__section">
          <header className="leaderboard__header">
            <h3 className="leaderboard__subtitle">Top Weekly Payouts</h3>
          </header>
          {renderWeeklyLeaderboard()}
        </section>
      </div>
    </div>
  );
}
