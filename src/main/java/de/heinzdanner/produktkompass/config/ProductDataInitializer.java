package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandRepository;
import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.CategoryRepository;
import de.heinzdanner.produktkompass.product.Company;
import de.heinzdanner.produktkompass.product.CompanyRepository;
import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.PackagingType;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.Source;
import de.heinzdanner.produktkompass.product.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ProductDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductDataInitializer.class);

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final SourceRepository sourceRepository;

    public ProductDataInitializer(ProductRepository productRepository,
                                 BrandRepository brandRepository,
                                 CategoryRepository categoryRepository,
                                 CompanyRepository companyRepository,
                                 SourceRepository sourceRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.companyRepository = companyRepository;
        this.sourceRepository = sourceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Category milkAlternatives = ensureCategory("Milchprodukte und Milchalternativen", "Milchprodukte sowie pflanzliche Ersatzprodukte.");
        Category chocolate = ensureCategory("Süßwaren und Schokolade", "Süßwaren, Schokolade und kakaohaltige Produkte.");
        Category coffee = ensureCategory("Kaffee und Tee", "Kaffee, Tee und ähnliche Heißgetränke.");
        Category beverages = ensureCategory("Getränke", "Erfrischungsgetränke und Getränkeprodukte.");
        Category pasta = ensureCategory("Nudeln und Teigwaren", "Nudeln, Pasta und Teigwaren.");
        Category grains = ensureCategory("Reis und Getreideprodukte", "Reis, Hafer, Müsli und andere Getreideprodukte.");
        ensureCategory("Snacks und Knabberartikel", "Salzige und süße Snacks.");
        ensureCategory("Brot und Backwaren", "Brot, Gebäck und Backwaren.");
        ensureCategory("Obst und Gemüse", "Frisches und verarbeitetes Obst und Gemüse.");
        ensureCategory("Tiefkühlprodukte", "Tiefgekühlte Lebensmittel.");
        Category pantry = ensureCategory("Konserven und Fertiggerichte", "Konserven, Fertigprodukte und schnelle Mahlzeiten.");
        ensureCategory("Saucen und Würzmittel", "Saucen, Würzmittel und Dressings.");

        Brand oatly = ensureBrand("Oatly", "Schwedische Marke für Hafergetränke und pflanzliche Produkte.");
        Brand alpro = ensureBrand("Alpro", "Internationale Pflanzenmilch-Marke mit Soja-, Hafer- und Mandelbasis.");
        Brand lindt = ensureBrand("Lindt & Sprüngli", "Schweizer Schokoladenmarke mit Fokus auf hochwertige Schokolade.");
        Brand ferrero = ensureBrand("Ferrero", "Italienische Lebensmittelmarke mit vielfältigem Süßwarenportfolio.");
        Brand nestle = ensureBrand("Nescafé", "Weltweite Kaffee-Marke von Nestlé.");
        Brand cocaCola = ensureBrand("Coca-Cola", "Internationale Getränke-Marke mit Erfrischungsgetränken.");
        Brand barilla = ensureBrand("Barilla", "Italienische Marke für Pasta und Teigwaren.");
        Brand ja = ensureBrand("ja!", "Preisgünstige Handelsmarke für Grundnahrungsmittel.");

        Company oatlyCompany = ensureCompany(
                "Oatly Group AB",
                "https://www.oatly.com",
                "Schweden",
                "Schwedisches Unternehmen mit Fokus auf Haferprodukte."
        );
        Company alproCompany = ensureCompany(
                "Danone S.A.",
                "https://www.danone.com",
                "Frankreich",
                "Internationale Lebensmittelgruppe mit mehreren Marken für pflanzliche Produkte."
        );
        Company lindtCompany = ensureCompany(
                "Lindt & Sprüngli AG",
                "https://www.lindt-spruengli.com",
                "Schweiz",
                "Schweizer Schokoladenunternehmen mit globalem Vertrieb."
        );
        Company ferreroCompany = ensureCompany(
                "Ferrero Group",
                "https://www.ferrero.com",
                "Italien",
                "Italienische Lebensmittelgruppe mit Süßwaren und Snacks."
        );
        Company nestleCompany = ensureCompany(
                "Nestlé S.A.",
                "https://www.nestle.com",
                "Schweiz",
                "Multinationales Lebensmittel- und Getränkeunternehmen."
        );
        Company cocaColaCompany = ensureCompany(
                "The Coca-Cola Company",
                "https://www.coca-cola.com",
                "USA",
                "Globales Getränkeunternehmen mit einer Vielzahl von Marken."
        );
        Company barillaCompany = ensureCompany(
                "Barilla G. e R. Fratelli S.p.A.",
                "https://www.barilla.com",
                "Italien",
                "Italienisches Lebensmittelunternehmen mit Schwerpunkt Pasta und Backwaren."
        );
        Company reweCompany = ensureCompany(
                "REWE Markt GmbH",
                "https://www.rewe.de",
                "Deutschland",
                "Deutsche Handelsgruppe mit Eigenmarken für den täglichen Bedarf."
        );

        Source oatlySource = ensureSource("Oatly Produktseite", "https://www.oatly.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(5));
        Source alproSource = ensureSource("Alpro Produktübersicht", "https://www.alpro.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(10));
        Source lindtSource = ensureSource("Lindt Produktübersicht", "https://www.lindt.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(7));
        Source ferreroSource = ensureSource("Ferrero Produktseite", "https://www.ferrero.com/de/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(14));
        Source nestleSource = ensureSource("Nescafé Produktseite", "https://www.nescafe.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(9));
        Source cocaColaSource = ensureSource("Coca-Cola Produktseite", "https://www.coca-cola.de", "Offizielle Produktseite", LocalDateTime.now().minusDays(11));
        Source barillaSource = ensureSource("Barilla Produktübersicht", "https://www.barilla.com/de-de", "Offizielle Produktseite", LocalDateTime.now().minusDays(8));
        Source reweSource = ensureSource("REWE Eigenmarken", "https://www.rewe.de/marken", "Markenübersicht", LocalDateTime.now().minusDays(6));

        ensureProduct("Oat Drink Original", "Haferdrink mit natürlicher Süße und neutralem Geschmack.", oatly, oatlyCompany,
                Set.of(milkAlternatives), Set.of(oatlySource), NutritionGrade.A, PackagingType.TETRA_PAK, "1 l", false,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.UNKNOWN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN));
        ensureProduct("Alpro Sojamilch", "Pflanzliche Milchalternative auf Sojabasis mit neutralem Geschmack.", alpro, alproCompany,
                Set.of(milkAlternatives), Set.of(alproSource), NutritionGrade.A, PackagingType.CARTON, "1 l", false,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.NO_ADDED_SUGAR, ProductLabel.LACTOSE_FREE, ProductLabel.GLUTEN_FREE));
        ensureProduct("Lindt Excellence 70%", "Schokolade mit hohem Kakaoanteil und kräftigem Geschmack.", lindt, lindtCompany,
                Set.of(chocolate), Set.of(lindtSource), NutritionGrade.C, PackagingType.PAPER, "100 g", false,
                IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGETARIAN));
        ensureProduct("Nutella Original", "Süße Schokoladencreme mit Haselnuss- und Kakaogeschmack.", ferrero, ferreroCompany,
                Set.of(chocolate), Set.of(ferreroSource), NutritionGrade.D, PackagingType.GLASS, "450 g", false,
                IngredientStatus.CONTAINS, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE));
        ensureProduct("Nescafé Gold", "Gemahlener Kaffee für klassische Zubereitungsformen.", nestle, nestleCompany,
                Set.of(coffee), Set.of(nestleSource), NutritionGrade.A, PackagingType.GLASS, "200 g", false,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.CONTAINS,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE));
        ensureProduct("Coca-Cola Original", "Kohlensäurehaltiges Erfrischungsgetränk mit klassischem Cola-Geschmack.", cocaCola, cocaColaCompany,
                Set.of(beverages), Set.of(cocaColaSource), NutritionGrade.E, PackagingType.PLASTIC, "1,25 l", false,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.CONTAINS,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE));
        ensureProduct("Naturjoghurt", "Einfacher Naturjoghurt als klassisches Milchprodukt für Frühstück und Küche.", ja, reweCompany,
                Set.of(milkAlternatives), Set.of(reweSource), NutritionGrade.B, PackagingType.PLASTIC, "500 g", true,
                IngredientStatus.CONTAINS, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE));
        ensureProduct("Schwarzer Tee English Breakfast", "Kräftiger Schwarztee für den Alltag.", ja, reweCompany,
                Set.of(coffee), Set.of(reweSource), NutritionGrade.A, PackagingType.PAPER, "20 Beutel", true,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.CONTAINS,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE));
        ensureProduct("Mineralwasser Medium", "Natürliches Mineralwasser mit mittlerer Kohlensäure.", ja, reweCompany,
                Set.of(beverages), Set.of(reweSource), NutritionGrade.A, PackagingType.PLASTIC, "1,5 l", true,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE, ProductLabel.ALCOHOL_FREE, ProductLabel.CAFFEINE_FREE));
        ensureProduct("Penne Rigate", "Klassische Hartweizenpasta für viele Alltagsgerichte.", barilla, barillaCompany,
                Set.of(pasta, grains), Set.of(barillaSource), NutritionGrade.A, PackagingType.PAPER, "500 g", false,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.CONTAINS, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.LACTOSE_FREE));
        ensureProduct("Passierte Tomaten", "Tomatenbasis für Pasta, Suppen und Saucen.", ja, reweCompany,
                Set.of(pantry), Set.of(reweSource), NutritionGrade.A, PackagingType.GLASS, "680 g", true,
                IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN, IngredientStatus.DOES_NOT_CONTAIN,
                Set.of(ProductLabel.VEGAN, ProductLabel.VEGETARIAN, ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE, ProductLabel.NO_ADDED_SUGAR));

        logDataQualitySummary();
    }

    private Brand saveBrand(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    private Brand ensureBrand(String name, String description) {
        return brandRepository.findAll().stream()
                .filter(brand -> name.equals(brand.getName()))
                .findFirst()
                .orElseGet(() -> saveBrand(name, description));
    }

    private Category saveCategory(String name, String description) {
        return categoryRepository.save(new Category(name, description));
    }

    private Category ensureCategory(String name, String description) {
        return categoryRepository.findAll().stream()
                .filter(category -> name.equals(category.getName()))
                .findFirst()
                .orElseGet(() -> saveCategory(name, description));
    }

    private Company saveCompany(String name, String website, String country, String description) {
        return companyRepository.save(new Company(name, website, country, description));
    }

    private Company ensureCompany(String name, String website, String country, String description) {
        return companyRepository.findAll().stream()
                .filter(company -> name.equals(company.getName()))
                .findFirst()
                .orElseGet(() -> saveCompany(name, website, country, description));
    }

    private Source saveSource(String title, String url, String type, LocalDateTime verifiedAt) {
        return sourceRepository.save(new Source(title, url, type, verifiedAt));
    }

    private Source ensureSource(String title, String url, String type, LocalDateTime verifiedAt) {
        return sourceRepository.findAll().stream()
                .filter(source -> title.equals(source.getTitle()))
                .findFirst()
                .orElseGet(() -> saveSource(title, url, type, verifiedAt));
    }

    private void saveProduct(String name, String description, Brand brand, Company company, Set<Category> categories, Set<Source> sources,
                             NutritionGrade nutritionGrade, PackagingType packagingType, String packageSize, Boolean madeInGermany,
                             IngredientStatus containsLactose, IngredientStatus containsGluten, IngredientStatus containsAlcohol,
                             IngredientStatus containsCaffeine, Set<ProductLabel> labels) {
        Product product = new Product(name, description);
        product.setBrand(brand);
        product.setCompany(company);
        product.setNutritionGrade(nutritionGrade);
        product.setPackagingType(packagingType);
        product.setPackageSize(packageSize);
        product.setMadeInGermany(madeInGermany);
        product.setContainsLactose(containsLactose);
        product.setContainsGluten(containsGluten);
        product.setContainsAlcohol(containsAlcohol);
        product.setContainsCaffeine(containsCaffeine);
        product.setLabels(labels);
        product.setCategories(categories);
        product.setSources(sources);
        productRepository.save(product);
    }

    private void ensureProduct(String name, String description, Brand brand, Company company, Set<Category> categories, Set<Source> sources,
                               NutritionGrade nutritionGrade, PackagingType packagingType, String packageSize, Boolean madeInGermany,
                               IngredientStatus containsLactose, IngredientStatus containsGluten, IngredientStatus containsAlcohol,
                               IngredientStatus containsCaffeine, Set<ProductLabel> labels) {
        Product existingProduct = productRepository.findAll().stream()
                .filter(product -> name.equals(product.getName()))
                .findFirst()
                .orElse(null);

        if (existingProduct != null) {
            existingProduct.setDescription(description);
            existingProduct.setBrand(brand);
            existingProduct.setCompany(company);
            existingProduct.setNutritionGrade(nutritionGrade);
            existingProduct.setPackagingType(packagingType);
            existingProduct.setPackageSize(packageSize);
            existingProduct.setMadeInGermany(madeInGermany);
            existingProduct.setContainsLactose(containsLactose);
            existingProduct.setContainsGluten(containsGluten);
            existingProduct.setContainsAlcohol(containsAlcohol);
            existingProduct.setContainsCaffeine(containsCaffeine);
            existingProduct.setLabels(labels);
            existingProduct.setCategories(categories);
            existingProduct.setSources(sources);
            productRepository.save(existingProduct);
            return;
        }

        saveProduct(name, description, brand, company, categories, sources, nutritionGrade, packagingType, packageSize,
                madeInGermany, containsLactose, containsGluten, containsAlcohol, containsCaffeine, labels);
    }

    private void logDataQualitySummary() {
        List<String> incompleteProducts = productRepository.findAllWithDetails().stream()
                .map(this::describeQualityIssues)
                .filter(issues -> !issues.isBlank())
                .toList();

        if (incompleteProducts.isEmpty()) {
            log.info("Datenqualitaet: keine unvollstaendigen Produkte gefunden.");
            return;
        }

        log.warn("Datenqualitaet: {} Produkt(e) mit unvollstaendigen Angaben gefunden.", incompleteProducts.size());
        incompleteProducts.forEach(issue -> log.warn("Datenqualitaet Detail: {}", issue));
    }

    private String describeQualityIssues(Product product) {
        List<String> issues = new ArrayList<>();

        if (isBlank(product.getName())) {
            issues.add("Name fehlt");
        }
        if (isBlank(product.getDescription())) {
            issues.add("Beschreibung fehlt");
        }
        if (product.getBrand() == null) {
            issues.add("Marke fehlt");
        }
        if (product.getCompany() == null) {
            issues.add("Unternehmen fehlt");
        }
        if (product.getCategories() == null || product.getCategories().isEmpty()) {
            issues.add("Kategorie fehlt");
        }
        if (product.getSources() == null || product.getSources().isEmpty()) {
            issues.add("Quelle fehlt");
        }
        if (product.getNutritionGrade() == null || NutritionGrade.UNKNOWN.equals(product.getNutritionGrade())) {
            issues.add("Nutri-Score fehlt/unklar");
        }
        if (product.getPackagingType() == null) {
            issues.add("Verpackung fehlt");
        }
        if (isBlank(product.getPackageSize())) {
            issues.add("Packungsgroesse fehlt");
        }
        if (product.getMadeInGermany() == null) {
            issues.add("Herkunftsangabe fehlt");
        }
        if (product.getContainsLactose() == null || IngredientStatus.UNKNOWN.equals(product.getContainsLactose())) {
            issues.add("Laktoseangabe fehlt/unklar");
        }
        if (product.getContainsGluten() == null || IngredientStatus.UNKNOWN.equals(product.getContainsGluten())) {
            issues.add("Glutenangabe fehlt/unklar");
        }
        if (product.getContainsAlcohol() == null || IngredientStatus.UNKNOWN.equals(product.getContainsAlcohol())) {
            issues.add("Alkoholangabe fehlt/unklar");
        }
        if (product.getContainsCaffeine() == null || IngredientStatus.UNKNOWN.equals(product.getContainsCaffeine())) {
            issues.add("Koffeinangabe fehlt/unklar");
        }
        if (product.getLabels() == null || product.getLabels().isEmpty()) {
            issues.add("Labels fehlen");
        }

        if (issues.isEmpty()) {
            return "";
        }

        return product.getName() + " -> " + String.join(", ", issues);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
