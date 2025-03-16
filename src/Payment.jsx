import React, { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { Elements } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import PaymentForm from "./PaymentForm";

const stripePromise = loadStripe("pk_test_51R1s9U4Ib6pQtdzfvbfx0FVqiANXCaAZMxVY6Nu8Eb0VxRIAncuKQ1DlIaVanRDOaejeavEyangNqFmWfHnG1oXI00Metr4woF"); // Your Stripe Public Key

const Payment = () => {
  const [searchParams] = useSearchParams();
  const name = searchParams.get("name");
  const price = searchParams.get("price");
  const amount = parseFloat(price.replace("₱", "").replace(",", "")) * 100; // Convert to cents
  const [clientSecret, setClientSecret] = useState("");

  // Fetch the client secret when component loads
  useEffect(() => {
    fetch("http://localhost:5000/create-payment-intent", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ amount, currency: "php" }), // Send amount to backend
    })
      .then((res) => res.json())
      .then((data) => setClientSecret(data.clientSecret))
      .catch((err) => console.error("Error fetching clientSecret:", err));
  }, [amount]);

  return (
    <div className="payment-container">
      <h1>Complete Your Payment</h1>
      <p>Subscription: {name}</p>
      <p>Amount: {price}</p>

      {clientSecret ? (
        <Elements stripe={stripePromise} options={{ clientSecret }}>
          <PaymentForm />
        </Elements>
      ) : (
        <p>Loading payment details...</p>
      )}
    </div>
  );
};

export default Payment;
