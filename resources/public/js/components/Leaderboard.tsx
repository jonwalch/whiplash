import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import "../../css/App.css";
import { Link } from "react-router-dom";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const"

export interface Leader {
  screen_name: string;
  score: number;
}

export function Leaderboard(props: any) {
  const { state, setState } = useContext(LoginContext);
  const [leaderboard, setLeaderboard] = useState<Leader[]>([]);

  useEffect(() => {
    getLeaderboard();
  }, []);

  const getLeaderboard = async () => {
    const response = await fetch(baseUrl + "v1/leaderboard/weekly",
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
    //   { screen_name: "fuck", score: 300 },
    //   { screen_name: "you", score: 100 }
    // ]);

    // if (response.status == 200) {
    //   setState({ userLoggedIn: true });
    // } else {
    //   alert(resp.message);
    // }
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
              <div key={leader.screen_name}>
                {leader.screen_name} {leader.score}
              </div>
            );
          })}
        </div>
        </div>
      );
    }
  };

  return (
    <div>
      <h3>This week's Leaderboard</h3>
      <Link to="/">Back to stream</Link>
      {renderContent()}
    </div>
  );
}
