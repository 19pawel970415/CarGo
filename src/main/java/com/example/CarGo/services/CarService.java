package com.example.CarGo.services;


import com.example.CarGo.db.*;
import com.example.CarGo.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLDataException;
import java.time.LocalDate;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CarService {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private final ReservationRepository reservationRepository;

    @Autowired
    private final LocationRepository locationRepository;

    @Autowired
    private final CarMakeRepository carMakeRepository;

    @Autowired
    private final SeatCountRepository seatCountRepository;

    public CarService(ReservationRepository reservationRepository, LocationRepository locationRepository, CarMakeRepository carMakeRepository, SeatCountRepository seatCountRepository) {
        this.reservationRepository = reservationRepository;
        this.locationRepository = locationRepository;
        this.carMakeRepository = carMakeRepository;
        this.seatCountRepository = seatCountRepository;
    }

    public List<Car> findAllCars() {
        return carRepository.findAll();
    }

    public List<Car> findCarsByLocation(Location location) {
        return carRepository.findByLocation(location);
    }

    public Optional<Car> findCarById(Long id) {
        return carRepository.findById(id);
    }

    public List<Car> findAvailableCars(LocalDate startDate, LocalDate endDate) {
        return carRepository.findAvailableCars(startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
    }

    public List<Car> findAvailableCarsInLocation(Location location, LocalDate startDate, LocalDate endDate) {
        return carRepository.findAvailableCarsInLocation(location.getCity(), startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59));
    }

    public List<Car> findCarsWithFilters(Location location,
                                         GearboxType gearboxType,
                                         ChassisType chassisType,
                                         Long seatCount,
                                         Integer yearMin,
                                         Integer yearMax,
                                         Double priceMin,
                                         Double priceMax,
                                         String make,
                                         FuelType fuelType,
                                         LocalDate startDate,
                                         LocalDate endDate) {
        return carRepository.findCarsWithFilters(location.getCity(),
                gearboxType,
                chassisType,
                seatCount,
                yearMin,
                yearMax,
                priceMin,
                priceMax,
                make,
                fuelType,
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59));
    }

    public void setCarReadyForRent(Long carId) {
        Optional<Car> carOptional = carRepository.findById(carId);
        if (carOptional.isPresent()) {
            Car car = carOptional.get();
            car.setStatus(CarStatus.READY_FOR_RENT);
            carRepository.save(car);
        }
    }

    @Async
    public void changeStatusToInServiceAndWait(Long carId) {
        Optional<Car> carOptional = carRepository.findById(carId);
        if (carOptional.isPresent()) {
            Car car = carOptional.get();
            car.setStatus(CarStatus.IN_SERVICE);
            carRepository.save(car);

            try {
                // set to a minute just as an example
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            car.setStatus(CarStatus.SERVICED);
            carRepository.save(car);
        }
    }

    public void updateCarStatuses() {
        LocalDateTime today = LocalDateTime.now()
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        List<Reservation> reservationsEndingToday = reservationRepository.findByReservationEnd(today);

        List<Car> carsToUpdate = reservationsEndingToday.stream()
                .map(Reservation::getCar)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        carsToUpdate.forEach(car -> car.setStatus(CarStatus.BEFORE_SERVICE));

        carRepository.saveAll(carsToUpdate);
    }

    public CarRequest getCarById(Long id) {
        Car car = carRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        return new CarRequest(
                car.getId(),
                car.getMake(),
                car.getModel(),
                car.getRegistrationNumber(),
                car.getVin(),
                car.getYearOfProduction(),
                car.getChassisType(),
                car.getGearboxType(),
                car.getFuelType(),
                car.getSeatCount(),
                car.getPricePerDay(),
                car.getLocation(),
                car.getStatus()
        );
    }

    @Transactional
    public void updateCar(CarUpdateRequest request) {
        Car car = carRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (request.getLocation() != null) {
            Location location = locationRepository.findByCity(request.getLocation().getCity())
                    .orElseGet(() -> locationRepository.save(new Location(request.getLocation().getCity())));

            car.setLocation(location);
        }

        car.setRegistrationNumber(request.getRegistrationNumber());
        car.setPricePerDay(request.getPricePerDay());

        carRepository.save(car);
    }

    @Transactional
    public void addCar(CarAddRequest request, MultipartFile image) {
        // Walidacja unikalności VIN i numeru rejestracyjnego
        try {
            validateCarUniqueness(request);
        } catch (SQLDataException e) {
            throw new RuntimeException(e);
        }

        // Znalezienie lub utworzenie lokalizacji
        Location location = findOrCreateLocation(request);

        // Sprawdzamy istnienie marki samochodu w bazie
        CarMake carMake = carMakeRepository.findByName(request.getMake().getName())
                .orElseGet(() -> createNewCarMake(request.getMake().getName()));

        if (request.getYearOfProduction() < 1900 || request.getYearOfProduction() > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("Year of production must be between 1900 and the current year.");
        }

        // Znalezienie SeatCount na podstawie liczby foteli
        SeatCount seatCount = seatCountRepository.findByCount(request.getSeatCount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Seat count not found"));

        // Zmiana dostępności na true
        seatCount.setAvailable(true);
        seatCountRepository.save(seatCount);  // Zapisanie zmiany dostępności foteli

        // Tworzenie samochodu
        Car car = new Car();
        car.setMake(carMake);  // Używamy znalezionej lub nowo utworzonej marki
        car.setModel(request.getModel());
        car.setRegistrationNumber(request.getRegistrationNumber());
        car.setVin(request.getVin());
        car.setYearOfProduction(request.getYearOfProduction());
        car.setChassisType(request.getChassisType());
        car.setGearboxType(request.getGearboxType());
        car.setFuelType(request.getFuelType());
        car.setSeatCount(seatCount);  // Przypisanie zaktualizowanego SeatCount
        car.setPricePerDay(request.getPricePerDay());
        car.setLocation(location);
        car.setStatus(CarStatus.READY_FOR_RENT);

        carRepository.save(car);

        saveCarImage(image, car.getId());
    }

    // Metoda pomocnicza do tworzenia nowej marki samochodu, jeśli nie istnieje
    private CarMake createNewCarMake(String makeName) {
        CarMake newCarMake = new CarMake();
        newCarMake.setName(makeName);
        return carMakeRepository.save(newCarMake);
    }

    // Metoda do znalezienia lub utworzenia lokalizacji
    private Location findOrCreateLocation(CarAddRequest request) {
        return locationRepository.findByCity(request.getLocation().getCity())
                .orElseGet(() -> locationRepository.save(new Location(request.getLocation().getCity())));
    }


    private void validateCarUniqueness(CarAddRequest request) throws SQLDataException {
        if (carRepository.existsByVin(request.getVin())) {
            throw new SQLDataException("Car with the same VIN already exists.");
        }
        if (carRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new SQLDataException("Car with the same Registration Number already exists.");
        }
    }

    public void deleteCar(Long carId) {
        if (hasActiveOrPendingReservations(carId)) {
            throw new IllegalStateException("You cannot delete this car as it is either rented now or booked for the future!");
        }

        List<Car> allCars = carRepository.findAll();

        // Pobranie marki samochodu
        String markName = carRepository.findById(carId).get().getMake().getName();

        // Liczba samochodów danej marki
        long carsWithTheSameMake = allCars.stream()
                .filter(c -> c.getMake().getName().equals(markName))
                .count();

        // Pobranie lokalizacji powiązanej z danym samochodem
        String cityName = carRepository.findById(carId).get().getLocation().getCity();

        // Liczba samochodów w tej samej lokalizacji
        long carsInTheSameLocation = allCars.stream()
                .filter(c -> c.getLocation().getCity().equals(cityName))
                .count();

        // Pobranie SeatCount powiązanego z samochodem
        Long seatCountId = carRepository.findById(carId).get().getSeatCount().getId();

        // Liczba samochodów z tym samym SeatCount
        long carsWithTheSameSeatCount = allCars.stream()
                .filter(c -> c.getSeatCount().getId() == seatCountId)
                .count();

        // Usunięcie samochodu
        carRepository.deleteById(carId);

        // Usunięcie marki, jeśli nie ma innych samochodów tej marki
        if (carsWithTheSameMake <= 1) {
            carMakeRepository.deleteById(carMakeRepository.findByName(markName).get().getId());
        }

        // Usunięcie lokalizacji, jeśli nie ma innych samochodów w tej lokalizacji
        if (carsInTheSameLocation <= 1) {
            locationRepository.deleteById(locationRepository.findByCity(cityName).get().getId());
        }


        // Usunięcie SeatCount, jeśli nie ma innych samochodów z tym samym SeatCount
        if (carsWithTheSameSeatCount <= 1) {
            Optional<SeatCount> seatCountbyId = seatCountRepository.findById(seatCountId);
            seatCountbyId.get().setAvailable(false);
            seatCountRepository.save(seatCountbyId.get());
        }

        // Usunięcie obrazu samochodu
        deleteCarImage(carId);
    }

    private void saveCarImage(MultipartFile image, long carNumber) {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image cannot be empty");
        }

        String uploadDir = "C:/Users/48721/IdeaProjects/CarGo/src/main/resources/static/images/";
        String fileName = carNumber + ".jpg"; // Możesz dostosować rozszerzenie

        Path filePath = Paths.get(uploadDir + fileName);
        try {
            Files.createDirectories(filePath.getParent()); // Upewnij się, że katalog istnieje
            image.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
        }

        try {
            Runtime.getRuntime().exec(new String[]{"git", "add", filePath.toString()});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteCarImage(Long carId) {
        // Ścieżka do obrazu samochodu
        String uploadDir = "C:/Users/48721/IdeaProjects/CarGo/src/main/resources/static/images/";
        String fileName = carId + ".jpg"; // Przy założeniu, że obrazy mają rozszerzenie .jpg
        Path filePath = Paths.get(uploadDir + fileName);

        // Usuwanie pliku, jeśli istnieje
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete image: " + e.getMessage(), e);
        }
    }

    public boolean hasActiveOrPendingReservations(Long carId) {
        // Pobierz wszystkie rezerwacje dla danego samochodu
        long numberOfReservationOnTheCar = reservationRepository.findAll().stream()
                .filter(r -> r.getCar().getId() == carId)
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE || r.getStatus() == ReservationStatus.PENDING)
                .count();

        if (numberOfReservationOnTheCar > 0) {
            return true;
        } else {
            return false;
        }
    }
}