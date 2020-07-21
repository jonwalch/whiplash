import React, {useState, ChangeEvent, useContext} from "react";
import {failedToFetch} from "./Home";
import { baseUrl } from "../config/const";
import {LoginContext} from "../contexts/LoginContext";

export function Suggestion(props: any) {
    const [suggestion, setSuggestion] = useState<string>("");
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const [suggestWaitingForResp, setSuggestWaitingForResp] = useState<boolean>(false);

    const createSuggestion = async () => {
        setSuggestWaitingForResp(true);
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

        // TODO add GA event
        setSuggestion("");
        setSuggestWaitingForResp(false);
    };

    const suggestionOnKeyPress = (e: any) => {
        const key = e.key;
        if (key == "Enter" && !toggleValid()) {
            createSuggestion();
        }
    };

    const toggleValid = () => {
        return suggestion === null ||
            suggestion === "" ||
            suggestion.length < 5 ||
            suggestWaitingForResp ||
            !loggedInState.userName ||
            loggedInState.status == "user.status/pending" ||
            loggedInState.status == "user.status/unauth";
    };

    const placeholderText = () => {
        if (!loggedInState.userName) {
            return "Sign up and log in to suggest a proposition!";
        } else if (loggedInState.status === "user.status/pending") {
            return "Check your email and click the link to suggest a proposition!";
        } else if (loggedInState.status === "user.status/unauth") {
           return "Sign up to suggest a proposition!"
        } else {
            return "Type your proposition suggestion here!";
        }
    };

    const renderSuggestion = () => {
        return (
            <form className="form form__suggestion"
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
                    placeholder={placeholderText()}
                    name="suggestion"
                    id="suggestion"
                />
                <button
                    className={"button form__button suggestion__button " + (!toggleValid() ? "is-active" : "")}
                    type="button"
                    onClick={createSuggestion}
                    disabled={toggleValid()}>
                    <div className={suggestWaitingForResp ? "loading" : ""}>
                        {suggestWaitingForResp ? "" : "Submit Suggestion"}
                    </div>
                </button>
            </form>
        );
    };

    if (props.twitchUsername === failedToFetch) {
        return (<></>);
    } else {
        return renderSuggestion();
    }
}
