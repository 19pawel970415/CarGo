package com.example.CarGo.models;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "cars")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false, unique = true)
    private String vin;

    @Column(nullable = false)
    private int yearOfProduction;

    @Enumerated(EnumType.STRING)
    private CarStatus status;

    @Column
    private int seatCount;

    @Column
    private String bodyType;

    @Column
    private String color;

    @Column
    private String category;

    @OneToMany(mappedBy = "car", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Reservation> reservations;

    // Getters and Setters
}