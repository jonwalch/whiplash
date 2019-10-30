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
      return <p>No scores yet for this week!</p>;
    } else {
      return (
        <table className="leaderboard">
          <thead>
            <tr>
              <th>User</th>
              <th>Score</th>
            </tr>
          </thead>
          <tbody>
            {leaderboard.map((leader: Leader) => {
              return (
                <tr key={leader.screen_name}>
                  <td>{leader.screen_name}</td>
                  <td>{leader.score}</td>
                </tr>
              );
            })}
          </tbody>
        </table><!-- .leaderboard -->
      );
    }
  };

  return (
    <section>
      <h2>Weekly Leaderboard</h3>
      {renderContent()}
    </section>
  );
}
