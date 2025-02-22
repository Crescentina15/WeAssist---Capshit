import React, { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import { useNavigate } from "react-router-dom";
import { db } from "./script/firebase";
import { ref, get, update } from "firebase/database";
import "./index.css";

const LawyerDetails = () => {
  const navigate = useNavigate();
  const { id } = useParams(); 
  const [lawyer, setLawyer] = useState(null);
  const [services, setServices] = useState([]);
  const [newService, setNewService] = useState("");

  useEffect(() => {
    const lawyerRef = ref(db, `lawyers/${id}`);
    get(lawyerRef)
      .then((snapshot) => {
        if (snapshot.exists()) {
          setLawyer(snapshot.val());
          setServices(snapshot.val().services || []);
        } else {
          console.error("Lawyer not found.");
        }
      })
      .catch((error) => console.error("Error fetching lawyer data:", error));
  }, [id]);

  const handleProfileImageChange = (e) => {
    const imageUrl = URL.createObjectURL(e.target.files[0]);
    setLawyer((prevLawyer) => ({ ...prevLawyer, profileImage: imageUrl }));
    const lawyerRef = ref(db, `lawyers/${id}`);
    update(lawyerRef, { profileImage: imageUrl });
  };

  const handleAddService = () => {
    if (newService.trim()) {
      const updatedServices = [...services, newService];
      setServices(updatedServices);
      setNewService("");
      const lawyerRef = ref(db, `lawyers/${id}`);
      update(lawyerRef, { services: updatedServices });
    }
  };

  if (!lawyer) return <p>Loading...</p>;

  return (
    <div className="Lawyerprofile-card">
      <h2>Lawyer Details</h2>

     
      <div className="profile-image-container">
          {lawyer.profileImage ? (
            <img src={lawyer.profileImage} alt="Profile" className="profile-image" />
          ) : (
            <div className="image-placeholder">No Image</div>
          )}
            <button className="upload-btn" onClick={() => document.getElementById("profile-upload").click()}>
              Add Profile
            </button>
          <input
            id="profile-upload"
            type="file"
            accept="image/*"
            onChange={handleProfileImageChange}
            style={{ display: "none" }} 
          />
      </div>

      
      <div className="profile-info">
          <p className="profile-info-item"><strong>Name:</strong> {lawyer.name}</p>
          <p className="profile-info-item"><strong>Email:</strong> {lawyer.email}</p>
          <p className="profile-info-item"><strong>Phone:</strong> {lawyer.phone}</p>
          <p className="profile-info-item"><strong>Specialization:</strong> {lawyer.specialization}</p>
          <p className="profile-info-item"><strong>License Number:</strong> {lawyer.licenseNumber}</p>
          <p className="profile-info-item"><strong>Experience:</strong> {lawyer.experience} years</p>
      </div>


      
      <div className="services-frame">
  <h3 className="services-title">Services Offered</h3>
  <div className="services-container">
    {services.length > 0 ? (
      <ul className="services-list">
        {services.map((service, index) => (
          <li key={index} className="service-item">{service}</li>
        ))}
      </ul>
    ) : (
      <p className="no-services">No services added yet.</p>
    )}
  </div>
</div>


      <input className="service-input"
        type="text"
        placeholder="Add new service"
        value={newService}
        onChange={(e) => setNewService(e.target.value)}
      />
      <button className="add-btnEdit" onClick={handleAddService}>Add Service</button>
      <button className="add-btnEdit" onClick={() => navigate("/")} >Cancel</button>

    </div>
  );
};

export default LawyerDetails;