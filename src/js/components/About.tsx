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
              <p>Whiplash is a free game that allows you to place virtual bets on live events. Whiplash is purely for entertainment purposes and no real money will be paid out. Whipcash is only used to play Whiplash, it is not an actual currency and does not have value.</p>
            </div>
          </header>
          <div className="article__container">
            <h2>FAQ</h2>
            <h3>How do I play?</h3>
            <p>After creating an account and verifying your email, you will start with $500 in Whipcash that can be used to place bets on live event streams. Bets are open for the duration of the proposition.</p>
            <p>To place a bet, simply enter the amount you want to bet in the "Bet Amount" box and click one of the two "Bet" buttons to select your projected outcome. You may place as many bets as you want while the proposition is open. You can bet on both sides of the proposition. After placing a bet, it will be displayed on the screen in the “Current Bets” section.</p>
            <p>All players, bets, and winnings will not be deleted. If you're logged in and drop below $100 Whipcash you will receive a small bailout. A user without an account will not receive a bailout.</p>
            <h3>How does Whiplash pick what the current proposition is?</h3>
            <p>We take live suggestions from the community! Enter your suggestion into the text box below the video and press Make Suggestion!</p>
            <h3>How are odds and payouts calculated?</h3>
            <p>Odds and payouts update in real-time and are based on <a href="https://en.wikipedia.org/wiki/Parimutuel_betting">parimutuel betting</a>.</p>
            <p>The winning side gets an additional payout bonus based on how many players that bet on that side.</p>
            <p>Payouts are rounded up to the nearest whole Whipcash.</p>
            <h3>How can I chat with other Whiplash users?</h3>
            <p>Join our <a href="https://discord.gg/GsG2G9t">Discord server</a>!</p>
            <p>Follow us on <a href="https://twitter.com/whiplashgg">Twitter</a>!</p>
            <h3>How does Whiplash decide which streamer to play?</h3>
            <p>We're partnered with the streamers we cover. If you're interested, DM Bazooka or Jawbreaker on Discord.</p>
            <h3>Why can’t I see the embedded Twitch chat?</h3>
            <p>You need to allow third party cookies in your browser settings.</p>
            <h3>How do I verify my email <address></address>?</h3>
            <p>Click the link in the email we sent you when you signed up.</p>
            <h3>Why didn’t I get a verification email?</h3>
            <p>Check your spam. If it’s not there, email us at <a href="mailto:support@whiplashesports.com">support@whiplashesports.com</a>.</p>
            <h3>How do I change my password?</h3>
            <p>Login and click your name in the header. It will bring you to your account page.</p>
            <h3>Why can't I bet?</h3>
            <p>If you're signed up and logged in, make sure your email is verified.</p>
            <h3>Why aren't I getting bailed out?</h3>
            <p>Make sure you verified your email address.</p>
            <h3>Why aren't I on the leaderboards?</h3>
            <p>You need to sign up for an account to appear on leaderboards.</p>
            <h3>How can I help support Whiplash?</h3>
            <p>You can tip us <a href="https://streamelements.com/whiplash_gg/tip">here</a>!</p>
          </div>
          <div className="article__container">
            <h2>Terms of Service</h2>
            <h3>Do not harass other players.</h3>
            <h3>No spamming or hate speech. </h3>
            <h3>Do not advertise unauthorized third party extensions or other websites.</h3>
            <h3>Do not exploit, abuse, or excessively use the system in a way that affects other players or the operation of the system.</h3>
            <h3>Your account may be deleted by an administrator for violating these terms.</h3>
            <p>NOTE: All Terms of Service also apply to the Whiplash Discord server.</p>
          </div>
        </article>
      </main>
      <Footer/>
    </>
  );
}
