import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import "../../css/App.css";
import { Link } from "react-router-dom";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"
import { useInterval } from "../common";

export interface Leader {
  user_name: string;
  score: number;
}

export function Leaderboard(props: any) {
  const { loggedInState, setLoggedInState} = useContext(LoginContext);
  const [leaderboard, setLeaderboard] = useState<Leader[]>([]);

  useEffect(() => {
    getLeaderboard();
  }, []);

  //every 5 minutes
  useInterval(() => {
      getLeaderboard();
  }, 300000);

  const getLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/weekly",
      {
        headers: { "Content-Type": "application/json" },
        method: "GET",
        mode: "same-origin",
        redirect: "error"
      }
    );
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
    setLeaderboard(resp);
    // setLeaderboard([
    //   { user_name: "fuck", score: 300 },
    //   { user_name: "you", score: 100 }
    // ]);

  };

  const renderContent = () => {
    if (leaderboard.length == 0) {
      return <div>No scores yet for this week!</div>;
    } else {
      return (
        <div>
          <h3>User, score</h3>
        <div>
          {leaderboard.map((leader: Leader) => {
            return (
              <div key={leader.user_name}>
                {leader.user_name} {leader.score}
              </div>
            );
          })}
        </div>
        </div>
      );
    }
  };

  return (
    <div className="leaderboard">
      <h3>Weekly Leaderboard</h3>
      {renderContent()}
    </div>
  );
}
