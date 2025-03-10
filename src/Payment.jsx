import React from "react";
import { useLocation } from "react-router-dom";

const Payment = () => {
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const planName = params.get("name");
  const planPrice = params.get("price");

  return (
    <div className="payment-container">
      <h1 style={{ color: "black" }}>Payment Page</h1>
      <p style={{ color: "black" }}>
        You selected: <strong>{planName}</strong>
      </p>
      <p style={{ color: "black" }}>
        Price: <strong>{planPrice}</strong>
      </p>
      <button className="pay-now-btn">Proceed to Payment</button>
    </div>
  );
};

export default Payment;
