import React, {useState} from "react";

//export interface HelloProps { compiler: string; framework: string; }

// 'HelloProps' describes the shape of props.
// State is never set so we use the '{}' type.
// export class Hello extends React.Component<HelloProps, {}> {
//     render() {
//         return <h1>Hello from {this.props.compiler} and {this.props.framework}!</h1>;
//     }
// }


export function Hello(props: any) {

    const handleClick = (team : string) => {
        setTeam(team)
    }
    const [on, setOn] = useState(false)
    const [team, setTeam] = useState("")
    //setOn(true)
    //TODO size video based on web browser size
    return (<div>
        <h2>Whiplash - Win While Watching</h2>
        <iframe
            src="https://player.twitch.tv/?channel=ramee&muted=false"
            height="576"
            width="1024"
            frameBorder="0"
            scrolling="no"
            allowFullScreen={true}>
        </iframe>
        <div>
            <button onClick={() => handleClick("Red Team")}>Red Team</button>
            <button onClick={() => handleClick("Blue Team")}>Blue Team</button>
        </div>
        <h1> You selected {team}</h1>
    </div>)
}
