import React, {useState, ChangeEvent, useContext} from "react";
import {failedToFetch} from "./Home";
import { baseUrl } from "../config/const";
import {LoginContext} from "../contexts/LoginContext";

export function Suggestion(props: any) {
    const [suggestion, setSuggestion] = useState<string>("");
    const { loggedInState, setLoggedInState } = useContext(LoginContext);

    const createSuggestion = async () => {
        const response = await fetch(baseUrl + "user/suggestion", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                text: suggestion,
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully made suggestion!");
        } else {
            alert(resp.message);
        }
        setSuggestion("")
    };

    const suggestionOnKeyPress = (e: any) => {
        const key = e.key;
        if (key == "Enter" && !toggleValid()) {
            createSuggestion();
        }
    };

    const toggleValid = () => {
        //TODO: add validation
        return suggestion === null || suggestion === "";
    };

    const renderSuggestion = () => {
        return (
            <form className="form form_suggestion"
                 onSubmit={(e: any) => e.preventDefault()}
            >
                <input
                    className="form__input suggestion__input"
                    value={suggestion}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => {
                        setSuggestion(e.currentTarget.value);
                    }}
                    onKeyPress={(e) => {suggestionOnKeyPress(e)}}
                    maxLength={100}
                    minLength={5}
                    placeholder="Type your prop bet suggestion here!"
                    name="suggestion"
                    id="suggestion"
                />
                <button
                    className="button form__button suggestion__button"
                    type="button"
                    onClick={createSuggestion}
                    disabled={toggleValid()}>
                    Submit Suggestion
                </button>
            </form>
        );
    };

    if (props.twitchUsername === failedToFetch ||
        loggedInState.userName === null ||
        loggedInState.status === "user.status/pending") {
        return (<></>);
    } else {
        return renderSuggestion();
    }
}