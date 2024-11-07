package com.example.CarGo;

import com.example.CarGo.DB.UserRepository;
import com.example.CarGo.Services.CarService;
import com.example.CarGo.Services.ReservationService;
import com.example.CarGo.Services.UserService;
import com.example.CarGo.models.Car;
import com.example.CarGo.models.Reservation;
import com.example.CarGo.models.ReservationStatus;
import com.example.CarGo.models.User;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Controller
public class GeneralController {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private CarService carService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;


    @GetMapping(value = {"/", "/index"})
    public String showIndex() {
        return "index";
    }
    @GetMapping("/about")
    public String showAbout() {
        return "about";
    }
    @GetMapping("/client")
    public String showClient() {
        return "client";
    }
    @GetMapping("/contact")
    public String showContact() {
        return "contact";
    }

    @GetMapping("/gallery")
    public String showGallery(
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        List<Car> cars;

        if (startDate != null && endDate != null) {
            // Check availability within date range, optionally filtered by location
            if (location != null && !location.isEmpty()) {
                cars = carService.findAvailableCarsInLocation(location, startDate, endDate);
            } else {
                cars = carService.findAvailableCars(startDate, endDate);
            }
        } else if (location != null && !location.isEmpty()) {
            // Only location is specified
            cars = carService.findCarsByLocation(location);
        } else {
            // No filters applied, show all cars
            cars = carService.findAllCars();
        }

        model.addAttribute("cars", cars);
        return "gallery";
    }
    @GetMapping("/services")
    public String showServices() {
        return "services";
    }
    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }
    @PostMapping("/register")
    public String registerUser(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("login") String login,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        // Walidacja hasła
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "register";
        }
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        user.setLogin(login);
        user.setPassword(password);
        // Rejestracja użytkownika
        String result = userService.registerUser(user);
        if (result.equals("User registered successfully")) {
            return "redirect:/login";
        } else {
            model.addAttribute("error", result);
            return "register";
        }
    }
    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }
    @PostMapping("/login")
    public String loginUser(
            @RequestParam("login") String login,
            @RequestParam("password") String password,
            HttpSession session,
            Model model) {
        // Wyszukiwanie użytkownika na podstawie loginu
        Optional<User> userOptional = userRepository.findByLogin(login);
        // Sprawdzenie, czy użytkownik istnieje i czy hasło jest poprawne
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(password)) {
                // Zapisanie użytkownika w sesji
                session.setAttribute("loggedInUser", user);
                return "redirect:/index";
            } else {
                model.addAttribute("error", "Invalid password");
            }
        } else {
            model.addAttribute("error", "User not found");
        }
        return "login";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam("email") String email,
            Model model) {

        boolean userExists = userRepository.existsByEmail(email);

        if (userExists) {
            try {
                userService.sendPasswordResetLink(email);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
            model.addAttribute("message", "A password reset link has been sent to your email.");
        } else {
            model.addAttribute("error", "Email address not found. Enter a correct email address.");
        }

        return "login";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam("email") String email, HttpSession session, Model model) {
        session.setAttribute("email", email);
        model.addAttribute("email", email);
        return "password_reset";
    }


    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            Model model) {

        String email = (String) session.getAttribute("email");  // Odczytanie emaila z sesji
        if (email == null) {
            model.addAttribute("error", "Email not found");
            return "password_reset";  // Zwrócenie na stronę resetowania, jeśli email nie jest w sesji
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "password_reset";  // Zwracamy do strony resetowania hasła
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setPassword(password);  // Ustawienie nowego hasła
            userService.updatePassword(email, password);  // Zaktualizowanie hasła w bazie
            model.addAttribute("message", "Password successfully reset");
            return "login";  // Po pomyślnym zresetowaniu hasła przekierowanie na stronę logowania
        } else {
            model.addAttribute("error", "Email not found");
            return "password_reset";  // Jeśli email nie istnieje, wracamy do formularza
        }
    }


    @GetMapping("/signout")
    public String signoutUser(HttpSession session) {
        session.invalidate();  // Usunięcie wszystkich danych z sesji
        return "redirect:/index";  // Przekierowanie na stronę główną
    }
    @GetMapping("/indexSignedIn")
    public String showIndexSignedIn() {
        return "indexSignedIn";
    }
    @GetMapping("/book/{carId}")
    public String showBookingForm(@PathVariable("carId") Long carId, Model model) {
        Optional<Car> car = carService.findCarById(carId);
        if (car.isPresent()) {
            model.addAttribute("car", car.get());
            return "book";
        } else {
            return "redirect:/gallery";
        }
    }

    @PostMapping("/reserve")
    public String reserveCar(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("returnLocation") String returnLocation,
            @RequestParam("carId") Long carId,
            HttpSession session,
            Model model) {

        // Check for the logged-in user in the session
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";  // Redirect to login if not authenticated
        }

        // Find the car by ID
        Optional<Car> carOptional = carService.findCarById(carId);
        if (!carOptional.isPresent()) {
            model.addAttribute("error", "Car not found");
            return "book";  // Redirect back to booking page if car is not found
        }

        // Create and set up reservation data
        Car car = carOptional.get();
        Reservation reservation = new Reservation();

        // Set reservation fields
        reservation.setId(null); // Ensure ID is generated automatically if using auto-generated IDs
        reservation.setReservationStart(LocalDateTime.of(LocalDate.parse(startDate), LocalTime.MIN));
        reservation.setReservationEnd(LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX));
        reservation.setStatus(ReservationStatus.PENDING); // Set initial status as per enum or String
        reservation.setCar(car);
        reservation.setUser(loggedInUser);
        reservation.setPickUpPoint(car.getLocation());
        reservation.setDropOfPoint(returnLocation);

        // Save reservation
        reservationService.saveReservation(reservation);

        // Redirect to a confirmation page or any other relevant page
        return "redirect:/confirmation";
    }

    @GetMapping("/confirmation")
    public String showConfirmation() {
        return "confirmation";
    }

    @PostMapping("/subscribe")
    @ResponseBody
    public ResponseEntity<String> subscribe(
            @RequestParam("email") String email) {

        try {
            userService.sendSubscriptionConfirmation(email);
            return ResponseEntity.ok("Thank you for subscribing! A confirmation email has been sent to your address.");
        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send subscription confirmation email. Please try again later.");
        }
    }

    @PostMapping("/sendMessage")
    public String sendMessage(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("message") String message,
            Model model) {

        String subject = "Message from " + email;
        String content = "Message: " + message + "\n\nThis message was sent by " + name + " from " + email;

        try {
            userService.sendContactFormMessage("19pawel970415@gmail.com", subject, content);
            model.addAttribute("message", "Your message has been sent successfully!");
        } catch (MessagingException e) {
            model.addAttribute("error", "Failed to send your message. Please try again later.");
        }

        return "contact";
    }

}