import React from "react";
import { useNavigate } from "react-router-dom";
import "./index.css"; 

const PlansSubscription = () => {
  const navigate = useNavigate();

  const plans = [
    {
      name: "1 Month Plan",
      price: "₱500",
      description: "Access premium features for one month.",
    },
    {
      name: "6 Months Plan",
      price: "₱2,500",
      description: "Enjoy premium features for six months at a discounted rate.",
    },
    {
      name: "1 Year Plan",
      price: "₱4,800",
      description: "Get the best value with a full-year subscription.",
    },
  ];

  const handleSubscribe = (plan) => {
    navigate(`/payment?name=${encodeURIComponent(plan.name)}&price=${encodeURIComponent(plan.price)}`);
  };

  return (
    <div className="plans-container">
      <h1 className="title">Plans & Subscription</h1>
      <div className="plans-grid">
        {plans.map((plan, index) => (
          <div key={index} className="plan-card">
            <span className="tag">{index === 2 ? "Best Value" : "For You"}</span>
            <h2>{plan.name}</h2>
            <p className="price">{plan.price} / period</p>
            <p className="description">{plan.description}</p>
            <button className="subscribe-btn" onClick={() => handleSubscribe(plan)}>
              Choose Plan
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default PlansSubscription;
