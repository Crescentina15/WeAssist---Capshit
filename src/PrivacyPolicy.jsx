import React from "react";
import { useNavigate } from "react-router-dom";

const PrivacyPolicy = () => {
  const navigate = useNavigate();

  return (
    <div className="privacy-container">
      <h1>Privacy Policy</h1>
      <p>
        Welcome to <strong>WeAssist</strong>. Your privacy is important to us, and we are committed 
        to protecting your personal information. This Privacy Policy explains how we collect, use, 
        store, and protect your data.
      </p>

     
      <h2>1. Information We Collect</h2>
      <p>When using WeAssist, we may collect the following types of information:</p>
      <ul>
        <li><strong>Personal Information:</strong> Name, email address, phone number, and contact details.</li>
        <li><strong>Billing & Payment Details:</strong> Secure payment data handled by third-party processors.</li>
        <li><strong>Legal Information:</strong> Documents and case details provided for legal consultations.</li>
        <li><strong>Usage Data:</strong> Log data, device information, and IP addresses.</li>
      </ul>

      
      <h2>2. How We Use Your Information</h2>
      <p>We use your data to:</p>
      <ul>
        <li>Provide legal services, consultations, and document processing.</li>
        <li>Enhance user experience and service efficiency.</li>
        <li>Maintain security and prevent unauthorized access.</li>
        <li>Process payments and manage user accounts.</li>
        <li>Comply with legal obligations and professional standards.</li>
      </ul>

      
      <h2>3. Data Protection & Security</h2>
      <p>
        We implement security measures to protect your personal data against unauthorized access, 
        loss, or misuse. This includes encryption, secure storage, and restricted access to sensitive information.
      </p>

      
      <h2>4. Third-Party Services</h2>
      <p>We do not sell or share your personal information with third parties, except in the following cases:</p>
      <ul>
        <li>When required by law or legal proceedings.</li>
        <li>With trusted third-party service providers, such as payment processors, under strict confidentiality agreements.</li>
        <li>If necessary to provide legal services, and only with your consent.</li>
      </ul>

     
      <h2>5. Data Retention</h2>
      <p>
        Your data is retained only as long as necessary to fulfill legal and operational requirements. 
        You may request deletion of your personal data, subject to legal constraints.
      </p>

      
      <h2>6. Your Rights & Choices</h2>
      <p>You have the right to:</p>
      <ul>
        <li>Access, correct, or delete your personal information.</li>
        <li>Opt out of marketing communications.</li>
        <li>Request a copy of the data we store about you.</li>
      </ul>
      <p>To exercise these rights, contact us at <strong>weassist@gmail.com</strong>.</p>

    
      <h2>7. Changes to This Policy</h2>
      <p>
        We may update this Privacy Policy periodically to reflect changes in regulations or our practices. 
        We encourage you to review this page regularly. Continued use of WeAssist after updates 
        constitutes acceptance of the revised policy.
      </p>

      
      <h2>8. Contact Us</h2>
      <p>If you have any questions regarding this Privacy Policy, please contact us at:</p>
      <p>📧 <strong>weassist@gmail.com</strong></p>

      
    </div>
  );
};

export default PrivacyPolicy;
