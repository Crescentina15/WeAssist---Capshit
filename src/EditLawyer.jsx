import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { db } from "./script/firebase";
import { ref, get, update, remove } from "firebase/database";
import "./index.css"; // Updated CSS filename

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
          setServices(data.services || []);
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
    <div className="lawyer-container">
      <h2 className="lawyer-title">Lawyer Details</h2>
      
      <div className="lawyer-content">
        <div className="lawyer-left">
          <div className="profile-image-container">
            <img 
              src={image || lawyer.profileImage || "https://via.placeholder.com/150?text=Profile"} 
              alt={lawyer.name} 
              className="profile-image" 
            />
          </div>
          <button 
            className="photo-button" 
            onClick={() => document.getElementById("fileUpload").click()}
          >
            Add Photo
          </button>
          <input 
            type="file" 
            accept="image/*" 
            id="fileUpload" 
            style={{ display: "none" }} 
            onChange={handleProfileImageChange} 
          />
        </div>
        
        <div className="lawyer-right">
          <div className="info-box">
            {isEditing ? (
              <div className="edit-form">
                <div className="form-row">
                  <label>Name:</label>
                  <input 
                    type="text" 
                    name="name" 
                    value={lawyer.name || ""} 
                    onChange={handleChange} 
                  />
                </div>
                <div className="form-row">
                  <label>Email:</label>
                  <input 
                    type="email" 
                    name="email" 
                    value={lawyer.email || ""} 
                    onChange={handleChange} 
                  />
                </div>
                <div className="form-row">
                  <label>Phone:</label>
                  <input 
                    type="text" 
                    name="phone" 
                    value={lawyer.phone || ""} 
                    onChange={handleChange} 
                  />
                </div>
                <div className="form-row">
                  <label>Specialization:</label>
                  <input 
                    type="text" 
                    name="specialization" 
                    value={lawyer.specialization || ""} 
                    onChange={handleChange} 
                  />
                </div>
                <div className="form-row">
                  <label>License Number:</label>
                  <input 
                    type="text" 
                    name="licenseNumber" 
                    value={lawyer.licenseNumber || ""} 
                    onChange={handleChange} 
                  />
                </div>
                <div className="form-row">
                  <label>Experience:</label>
                  <input 
                    type="text" 
                    name="experience" 
                    value={lawyer.experience || ""} 
                    onChange={handleChange} 
                  />
                </div>
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
            <>
              <div className="services-section">
                <h3 className="services-title">Services Offered</h3>
                <div className="services-list-container">
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
              
              <div className="add-service-container">
                <input
                  className="service-input"
                  type="text"
                  placeholder="Add new service"
                  value={newService}
                  onChange={(e) => setNewService(e.target.value)}
                />
                <button className="add-service-btn" onClick={handleAddService}>+</button>
              </div>
            </>
          )}
          
          <div className="action-buttons">
            {isEditing ? (
              <>
                <button className="action-btn save" onClick={handleSave}>Save</button>
                <button className="action-btn cancel" onClick={handleCancel}>Cancel</button>
              </>
            ) : (
              <>
                <button className="action-btn update" onClick={handleEditToggle}>Update</button>
                <button 
                  className="action-btn delete" 
                  onClick={() => {
                    if (window.confirm("Are you sure you want to delete this lawyer?")) {
                      remove(ref(db, `lawyers/${id}`)).then(() => navigate("/"));
                    }
                  }}
                >
                  Delete
                </button>
                <button className="action-btn back" onClick={() => navigate("/")}>Back</button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default LawyerDetails;