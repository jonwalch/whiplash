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
      return <div>No payouts yet for this week!</div>;
    } else {
      return (
        <div>
          <h3>User, Payout</h3>
          <div>
            {weeklyLeaderboard.map((leader: WeeklyLeader) => {
              return (
                <div key={leader.user_name}>
                  {leader.user_name} ${leader.payout}
                </div>
              );
            })}
          </div>
        </div>
      );
    }
  };

  const renderLeaderboard = () => {
    return (
      <div>
        <h3>User, Total Cash</h3>
        <div>
          {leaderboard.map((leader: Leader) => {
            return (
              <div key={leader.user_name}>
                {leader.user_name} ${leader.cash}
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div>
      <div className="leaderboard">
        <h3>All Time Top Ten</h3>
        {renderLeaderboard()}
        <h3>Weekly Leaderboard</h3>
        {renderWeeklyLeaderboard()}
      </div>
    </div>
  );
}
