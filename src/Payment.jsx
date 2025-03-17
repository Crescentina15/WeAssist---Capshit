import React, { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { Elements } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import PaymentForm from "./PaymentForm";

const stripePromise = loadStripe("pk_test_51R1s9U4Ib6pQtdzfvbfx0FVqiANXCaAZMxVY6Nu8Eb0VxRIAncuKQ1DlIaVanRDOaejeavEyangNqFmWfHnG1oXI00Metr4woF");

const Payment = () => {
  const [searchParams] = useSearchParams();
  const name = searchParams.get("name");
  const price = searchParams.get("price");
  const amount = parseFloat(price.replace("₱", "").replace(",", "")) * 100; // Convert to cents
  const [clientSecret, setClientSecret] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Fetch payment intent when component loads
    const fetchPaymentIntent = async () => {
      try {
        setLoading(true);
        const response = await fetch("http://localhost:5000/create-payment-intent", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ 
            amount, 
            currency: "php" 
          }),
        });
        
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        setClientSecret(data.clientSecret);
      } catch (err) {
        console.error("Error fetching payment intent:", err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchPaymentIntent();
  }, [amount]);

  // Prepare appearance options for the Elements provider
  const appearance = {
    theme: 'stripe',
    variables: {
      colorPrimary: '#6772e5',
    },
  };

  // Options for Elements provider
  const options = clientSecret ? {
    clientSecret,
    appearance,
  } : {};

  return (
    <div className="payment-container">
      <h1>Complete Your Payment</h1>
      <p>Subscription: {name}</p>
      <p>Amount: {price}</p>

      {loading && <p>Loading payment form...</p>}
      {error && <p className="error">Error: {error}</p>}
      
      {clientSecret && (
        <Elements stripe={stripePromise} options={options}>
          <PaymentForm />
        </Elements>
      )}
    </div>
  );
};

export default Payment;