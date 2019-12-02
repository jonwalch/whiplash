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
      <table className="leaderboard">
        <thead>
          <tr>
            <th>User</th>
            <th>Total Cash</th>
          </tr>
        </thead>
        <tbody>
          {leaderboard.map((leader: Leader) => {
            return (
              <tr key={leader.user_name}>
                <td>{leader.user_name}</td>
                <td>{leader.cash}</td>
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
    setWeeklyLeaderboard(resp);
  };

  const renderWeeklyLeaderboard = () => {
    if (weeklyLeaderboard.length == 0) {
      return <p>No payouts yet for this week!</p>;
    } else {
      return (
        <table className="leaderboard">
          <thead>
            <tr>
              <th>User</th>
              <th>Payout</th>
            </tr>
          </thead>
          <tbody>
            {weeklyLeaderboard.map((leader: WeeklyLeader) => {
              return (
                <tr key={leader.user_name}>
                  <td>{leader.user_name}</td>
                  <td>{leader.payout}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      );
    }
  };

  return (
    <div className="container">
      <section>
        <h2>All Time Top Ten</h2>
        {renderLeaderboard()}
      <section>
      </section>
        <h2>Weekly Leaderboard</h2>
        {renderWeeklyLeaderboard()}
      </section>
    </div>
  );
}
