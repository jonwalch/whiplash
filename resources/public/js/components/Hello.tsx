import React, { useState, useEffect } from "react";

//export interface HelloProps { compiler: string; framework: string; }

// 'HelloProps' describes the shape of props.
// State is never set so we use the '{}' type.
// export class Hello extends React.Component<HelloProps, {}> {
//     render() {
//         return <h1>Hello from {this.props.compiler} and {this.props.framework}!</h1>;
//     }
// }

export function Hello(props: any) {
  const [team, setTeam] = useState("");
  const [streamURL, setURL] = useState("");
  useEffect(() => {
    getStream();
  }, []); // run this only on creation
  // can re render if given prop args in the []

  const getStream = async () => {
    const response = await fetch("http://localhost:3000/v1/stream", {
      headers: { "Content-Type": "application/json" }
    });
    const resp = await response.json();
    const url = resp["live_url"];
    setURL(url);
  };

  const handleClick = (team: string) => {
    setTeam(team);
  };

  //TODO size video based on web browser size
  return (
    <>
      <div>
        <h2>Whiplash - Win While Watching</h2>
        {streamURL && (
          <div>
            <iframe
              src={streamURL + "&muted=false"} //"https://player.twitch.tv/?channel=ramee&muted=false"
              height="576"
              width="1024"
              frameBorder="0"
              scrolling="no"
              allow="autoplay"
              allowFullScreen={true}
            ></iframe>
            <div>
              <button onClick={() => handleClick("Red Team")}>Red Team</button>
              <button onClick={() => handleClick("Blue Team")}>
                Blue Team
              </button>
            </div>
            <h1> You selected {team}</h1>
          </div>
        )}
      </div>
    </>
  );
}
