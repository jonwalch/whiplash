import React, {useState, useEffect, ChangeEvent, useContext} from "react";
import {LoginContext} from "../contexts/LoginContext";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {baseUrl} from "../config/const";
import {logout} from "../common/logout";
import {useInterval} from "../common";
import { getEvent, getProp} from "../common/stream"
import {getUser} from "../common/getUser";

export function Control(props: any) {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const [eventTitle, setEventTitle] = useState("");
    const [twitchUser, setTwitchUser] = useState("");
    const [propText, setPropText] = useState("");
    const [propInfo, setPropInfo] = useState({});
    const [eventInfo, setEventInfo] = useState({});

    useEffect(() => {
        getEvent().then((event) => {setEventInfo(event)});
        getProp().then((event) => {setPropInfo(event)});
    }, []);

    useInterval(async () => {
        if (loggedInState.status == "user.status/admin") {
            setEventInfo(await getEvent());
            setPropInfo(await getProp());
        }
    }, 3000);

    const createEvent = async () => {
        const response = await fetch(baseUrl + "admin/event", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                title: eventTitle,
                twitch_user: twitchUser,
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully started event")
        } else {
            alert(resp.message)
        }
    };

    const endEvent = async () => {
        const response = await fetch(baseUrl + "admin/event/end", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully ended current event")
        } else {
            alert(resp.message)
        }
    };

    const createProp = async () => {
        const response = await fetch(baseUrl + "admin/prop", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                text: propText,
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully created proposition")
        } else {
            alert(resp.message)
        }
    };

    const endProp = async (result:boolean) => {
        const response = await fetch(baseUrl + "admin/prop/end", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                result: result,
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully ended proposition")
        } else {
            alert(resp.message)
        }
    };

    function renderAccountMarkup() {
        if (loggedInState.status == "user.status/admin") {
            return (
                <>
                    <div className="form__group">
                        <label className="form__label" htmlFor="eventTitle">Event Title</label>
                        <input
                            className="form__input"
                            value={eventTitle}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setEventTitle(e.currentTarget.value);
                            }}
                            // type="password"
                            maxLength={100}
                            minLength= {5}
                            name="eventTitle"
                            id="eventTitle"
                        />
                    </div>
                    <div className="form__group">
                        <label className="form__label" htmlFor="twitchUser">Event Twitch User</label>
                        <input
                            className="form__input"
                            value={twitchUser}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setTwitchUser(e.currentTarget.value);
                            }}
                            // type="password"
                            maxLength={50}
                            minLength= {4}
                            name="twitchUser"
                            id="twitchUser"
                        />
                    </div>
                    <button
                        className="button twitch__button"
                        // TODO: remove inline style
                        style = {{marginRight: "30px"}}
                        type="button"
                        onClick={() => {
                            createEvent()
                        }}>
                        Create Event
                    </button>
                    <button
                        className="button twitch__button"
                        type="button"
                        onClick={() => {
                            endEvent()
                        }}>
                        End Event
                    </button>
                    <div className="form__group">
                        <label className="form__label" htmlFor="propText">Proposition Text</label>
                        <input
                            className="form__input"
                            value={propText}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setPropText(e.currentTarget.value);
                            }}
                            // type="password"
                            maxLength={100}
                            minLength= {5}
                            name="propText"
                            id="propText"
                        />
                    </div>
                    <button
                        className="button twitch__button"
                        // TODO: remove inline style
                        style = {{marginRight: "30px"}}
                        type="button"
                        onClick={() => {
                            createProp()
                        }}>
                        Create Proposition
                    </button>
                    <button
                        className="button twitch__button"
                        style = {{marginRight: "30px"}}
                        type="button"
                        onClick={() => {
                            endProp(true)
                        }}>
                        Proposition outcome: True
                    </button>
                    <button
                        className="button twitch__button"
                        type="button"
                        onClick={() => {
                            endProp(false)
                        }}>
                        Proposition outcome: False
                    </button>
                    <div>Current Event:</div>
                    <div>{JSON.stringify(eventInfo)}</div>
                    <div>Current Proposition:</div>
                    <div>{JSON.stringify(propInfo)}</div>
                </>
            );
        } else {
            return (
                <>
                    <div>404</div>
                </>
            );
        }
    }

    return (
        <>
            <Header/>
            <main id="content" role="main" className="article">
                {renderAccountMarkup()}
            </main>
            <Footer/>
        </>
    );
}
