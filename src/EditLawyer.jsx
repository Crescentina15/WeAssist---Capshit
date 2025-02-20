import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { db, storage } from "./script/firebase";
import { ref, get, update } from "firebase/database";
import { ref as storageRef, uploadBytes, getDownloadURL } from "firebase/storage";

const EditLawyer = () => {
  const { lawyerId } = useParams();
  const navigate = useNavigate();
  const [lawyer, setLawyer] = useState(null);
  const [image, setImage] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [loading, setLoading] = useState(true);

  // Fetch lawyer data from Firebase
  useEffect(() => {
    const fetchLawyer = async () => {
      try {
        const lawyerRef = ref(db, `lawyers/${lawyerId}`);
        const snapshot = await get(lawyerRef);
        if (snapshot.exists()) {
          setLawyer(snapshot.val());
          setImagePreview(snapshot.val().profilePicture || null);
        } else {
          console.error("Lawyer not found.");
        }
      } catch (error) {
        console.error("Error fetching lawyer data:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchLawyer();
  }, [lawyerId]);

  // Handle input changes
  const handleChange = (e) => {
    setLawyer({ ...lawyer, [e.target.name]: e.target.value });
  };

  // Handle image selection
  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setImage(file);
      setImagePreview(URL.createObjectURL(file)); // Show preview before uploading
    }
  };

  // Handle form submission
  const handleUpdate = async (e) => {
    e.preventDefault();
    if (!lawyer) return;

    let imageUrl = lawyer.profilePicture;

    // If a new image is selected, upload it to Firebase Storage
    if (image) {
      const imageStorageRef = storageRef(storage, `lawyer_profiles/${lawyerId}`);
      await uploadBytes(imageStorageRef, image);
      imageUrl = await getDownloadURL(imageStorageRef);
    }

    // Update lawyer details in Firebase Database
    const lawyerRef = ref(db, `lawyers/${lawyerId}`);
    await update(lawyerRef, {
      name: lawyer.name,
      specialization: lawyer.specialization,
      services: lawyer.services,
      profilePicture: imageUrl,
    });

    console.log("Lawyer details updated successfully");
    navigate("/"); // Redirect back to admin panel
  };

  if (loading) return <p>Loading...</p>;
  if (!lawyer) return <p>Lawyer not found.</p>;

  return (
    <div className="edit-lawyer">
      <h2>Edit Lawyer</h2>
      <form onSubmit={handleUpdate}>
        <label>Name:</label>
        <input type="text" name="name" value={lawyer.name || ""} onChange={handleChange} required />

        <label>Specialization:</label>
        <input type="text" name="specialization" value={lawyer.specialization || ""} onChange={handleChange} required />

        <label>Services:</label>
        <input type="text" name="services" value={lawyer.services || ""} onChange={handleChange} required />

        <label>Profile Picture:</label>
        <input type="file" accept="image/*" onChange={handleImageChange} />
        {imagePreview && <img src={imagePreview} alt="Profile Preview" width="100" />}

        <button type="submit">Update</button>
      </form>
    </div>
  );
};

export default EditLawyer;
