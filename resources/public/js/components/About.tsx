import React from "react";
import {Header} from "./Header";
import {Footer} from "./Footer";

export function About() {
  return (
    <>
      <Header/>
      <main id="content" role="main" className="article">
        <article>
          <header className="article__header">
            <div className="article__container">
              <h2>About Us</h2>
              <h3>What is Whiplash?</h3>
              <p>Whiplash is a free game that allows you to place virtual bets on live competitive events. Whiplash is purely for entertainment purposes and no real money will be paid out. Whiplash Cash is only used to play Whiplash, it is not an actual currency and does not have value.</p>
              <p>Betting on the winner of each round is coming soon!</p>
            </div>
          </header>
          <div className="article__container">
            <h2>FAQ</h2>
            <h3>How do I play?</h3>
            <p>After creating an account and verifying your email, you will start with $500 in Whiplash Cash that can be used to place bets on live competitive event streams. Bets are open for the entire game.</p>
            <p>To place a bet, simply enter the amount you want to bet in the "Bet Amount" box and click one of the two "Bet" buttons to select your team. You may place as many bets as you want while bets are open. You can bet on as many teams as you want to. After placing a bet, it will be displayed on the screen in the “Current Bets” section.</p>
            <p>All players, bets, and winnings will not be deleted. If you drop below $100 Whiplash Cash you will receive a small bailout.</p>
            <h3>How are odds and payouts calculated?</h3>
            <p>Odds and payouts update in real-time and are based on <a href="https://en.wikipedia.org/wiki/Parimutuel_betting">parimutuel betting</a>.</p>
            <p>Payouts are rounded down to the nearest whole dollar.</p>
            <h3>How can I chat with other Whiplash users?</h3>
            <p>Join our <a href="https://discord.gg/B9vSqy">Discord server</a>!</p>
            <h3>How does Whiplash decide which stream to play?</h3>
            <p>We play the most popular esports CS:GO match on Twitch.</p>
            <h3>Why can’t I see the embedded Twitch chat?</h3>
            <p>You need to allow third party cookies in your browser settings.</p>
            <h3>Why didn’t I get a verification email?</h3>
            <p>Check your spam. If it’s not there, email us at <a href="mailto:support@whiplashesports.com">support@whiplashesports.com</a>.</p>
          </div>
          <div className="article__container">
            <h2>Terms of Service</h2>
            <h3>Do not advertise unauthorized third party extensions or other websites.</h3>
            <h3>Do not exploit, abuse, or excessively use the system in a way that affects other players or the operation of the system.</h3>
            <h3>Your account may be deleted by an administrator for violating these terms.</h3>
          </div>
        </article>
      </main>
      <Footer/>
    </>
  );
}
