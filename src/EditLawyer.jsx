import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { db } from "./script/firebase";
import { ref, get, update, remove } from "firebase/database";
import "./index.css";

const LawyerDetails = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const [lawyer, setLawyer] = useState(null);
  const [services, setServices] = useState([]);
  const [newService, setNewService] = useState("");
  const [image, setImage] = useState(null);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    const lawyerRef = ref(db, `lawyers/${id}`);
    get(lawyerRef)
      .then((snapshot) => {
        if (snapshot.exists()) {
          const data = snapshot.val();
          setLawyer(data);
          setServices(data.services || []); // Ensure services is an array
        } else {
          console.error("Lawyer not found.");
        }
      })
      .catch((error) => console.error("Error fetching lawyer data:", error));
  }, [id]);

  const handleProfileImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const imageUrl = URL.createObjectURL(file);
      setImage(imageUrl);
      setLawyer((prevLawyer) => ({ ...prevLawyer, profileImage: imageUrl }));
      update(ref(db, `lawyers/${id}`), { profileImage: imageUrl });
    }
  };

  const handleEditToggle = () => {
    setIsEditing(true);
  };

  const handleCancel = () => {
    setIsEditing(false);
  };

  const handleSave = () => {
    update(ref(db, `lawyers/${id}`), lawyer)
      .then(() => {
        alert("Lawyer updated successfully.");
        setIsEditing(false);
      })
      .catch((error) => console.error("Error updating lawyer:", error));
  };

  const handleChange = (e) => {
    setLawyer({ ...lawyer, [e.target.name]: e.target.value });
  };

  const handleAddService = () => {
    if (newService.trim()) {
      const updatedServices = [...services, newService];

      // Update state
      setServices(updatedServices);
      setNewService("");

      // Update Firebase database
      update(ref(db, `lawyers/${id}`), { services: updatedServices })
        .then(() => {
          console.log("Service added and saved to database successfully.");
        })
        .catch((error) => {
          console.error("Error updating services in database:", error);
        });
    }
  };

  if (!lawyer) return <p>Loading...</p>;

  return (
    <div className="Lawyerprofile-card">
      <h2>Lawyer Details</h2>
      <div className="profile-image-container">
        <img src={image || lawyer.profileImage || "default.jpg"} alt="" className="profile-image" />
        <button className="photo-button" onClick={() => document.getElementById("fileUpload").click()}>
          Add Photo
        </button>
        <input type="file" accept="image/*" id="fileUpload" style={{ display: "none" }} onChange={handleProfileImageChange} />
      </div>

      <div className="profile-info">
        {isEditing ? (
          <div className="edit-form">
            <input type="text" name="name" value={lawyer.name} onChange={handleChange} className="input-field" />
            <input type="email" name="email" value={lawyer.email} onChange={handleChange} className="input-field" />
            <input type="text" name="phone" value={lawyer.phone} onChange={handleChange} className="input-field" />
            <input type="text" name="specialization" value={lawyer.specialization} onChange={handleChange} className="input-field" />
            <input type="text" name="licenseNumber" value={lawyer.licenseNumber} onChange={handleChange} className="input-field" />
            <input type="text" name="experience" value={lawyer.experience} onChange={handleChange} className="input-field" />
          </div>
        ) : (
          <>
            <p><strong>Name:</strong> {lawyer.name}</p>
            <p><strong>Email:</strong> {lawyer.email}</p>
            <p><strong>Phone:</strong> {lawyer.phone}</p>
            <p><strong>Specialization:</strong> {lawyer.specialization}</p>
            <p><strong>License Number:</strong> {lawyer.licenseNumber}</p>
            <p><strong>Experience:</strong> {lawyer.experience} years</p>
          </>
        )}
      </div>

      {!isEditing && (
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
      )}

      {!isEditing && (
        <div className="add-service-container">
          <input
            className="service-input"
            type="text"
            placeholder="Add new service"
            value={newService}
            onChange={(e) => setNewService(e.target.value)}
          />
          <button className="add-btn-small" onClick={handleAddService}>+</button>
        </div>
      )}

      {isEditing ? (
        <>
          <button className="add-btnEdit" onClick={handleSave}>Save</button>
          <button className="add-btnEdit" onClick={handleCancel}>Cancel</button>
        </>
      ) : (
        <>
          <button className="add-btnEdit" onClick={handleEditToggle}>Update</button>
          <button className="add-btnEdit" onClick={() => remove(ref(db, `lawyers/${id}`)).then(() => navigate("/"))}>Delete</button>
          <button className="add-btnEdit" onClick={() => navigate("/")}>Cancel</button>
        </>
      )}
    </div>
  );
};

export default LawyerDetails;
