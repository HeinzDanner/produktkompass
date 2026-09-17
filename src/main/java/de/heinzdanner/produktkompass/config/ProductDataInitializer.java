package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandRepository;
import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.CategoryRepository;
import de.heinzdanner.produktkompass.product.Company;
import de.heinzdanner.produktkompass.product.CompanyRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.Source;
import de.heinzdanner.produktkompass.product.SourceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Component
public class ProductDataInitializer implements ApplicationRunner {

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
        Category milkAlternatives = ensureCategory("Milchalternativen", "Pflanzliche Ersatzprodukte auf Basis von Hafer, Soja oder Kokos.");
        Category chocolate = ensureCategory("Schokolade", "Süßwaren mit Kakao und Schokoladenbasis.");
        Category coffee = ensureCategory("Kaffee", "Getränke und Produkte auf Kaffeebasis.");
        Category beverages = ensureCategory("Getränke", "Erfrischungsgetränke und Getränkeprodukte.");

        Brand oatly = ensureBrand("Oatly", "Schwedische Marke für Hafergetränke und pflanzliche Produkte.");
        Brand alpro = ensureBrand("Alpro", "Internationale Pflanzenmilch-Marke mit Soja-, Hafer- und Mandelbasis.");
        Brand lindt = ensureBrand("Lindt & Sprüngli", "Schweizer Schokoladenmarke mit Fokus auf hochwertige Schokolade.");
        Brand ferrero = ensureBrand("Ferrero", "Italienische Lebensmittelmarke mit vielfältigem Süßwarenportfolio.");
        Brand nestle = ensureBrand("Nescafé", "Weltweite Kaffee-Marke von Nestlé.");
        Brand cocaCola = ensureBrand("Coca-Cola", "Internationale Getränke-Marke mit Erfrischungsgetränken.");

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

        Source oatlySource = ensureSource("Oatly Produktseite", "https://www.oatly.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(5));
        Source alproSource = ensureSource("Alpro Produktübersicht", "https://www.alpro.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(10));
        Source lindtSource = ensureSource("Lindt Produktübersicht", "https://www.lindt.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(7));
        Source ferreroSource = ensureSource("Ferrero Produktseite", "https://www.ferrero.com/de/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(14));
        Source nestleSource = ensureSource("Nescafé Produktseite", "https://www.nescafe.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(9));
        Source cocaColaSource = ensureSource("Coca-Cola Produktseite", "https://www.coca-cola.de", "Offizielle Produktseite", LocalDateTime.now().minusDays(11));

        ensureProduct("Oat Drink Original", "Haferdrink mit natürlicher Süße und neutralem Geschmack.", oatly, oatlyCompany, Set.of(milkAlternatives), Set.of(oatlySource));
        ensureProduct("Alpro Sojamilch", "Pflanzliche Milchalternative auf Sojabasis mit neutralem Geschmack.", alpro, alproCompany, Set.of(milkAlternatives), Set.of(alproSource));
        ensureProduct("Lindt Excellence 70%", "Schokolade mit hohem Kakaoanteil und kräftigem Geschmack.", lindt, lindtCompany, Set.of(chocolate), Set.of(lindtSource));
        ensureProduct("Nutella Original", "Süße Schokoladencreme mit Haselnuss- und Kakaogeschmack.", ferrero, ferreroCompany, Set.of(chocolate), Set.of(ferreroSource));
        ensureProduct("Nescafé Gold", "Gemahlener Kaffee für klassische Zubereitungsformen.", nestle, nestleCompany, Set.of(coffee), Set.of(nestleSource));
        ensureProduct("Coca-Cola Original", "Kohlensäurehaltiges Erfrischungsgetränk mit klassischem Cola-Geschmack.", cocaCola, cocaColaCompany, Set.of(beverages), Set.of(cocaColaSource));
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

    private void saveProduct(String name, String description, Brand brand, Company company, Set<Category> categories, Set<Source> sources) {
        Product product = new Product(name, description);
        product.setBrand(brand);
        product.setCompany(company);
        product.setCategories(categories);
        product.setSources(sources);
        productRepository.save(product);
    }

    private void ensureProduct(String name, String description, Brand brand, Company company, Set<Category> categories, Set<Source> sources) {
        if (productRepository.findAll().stream().anyMatch(product -> name.equals(product.getName()))) {
            return;
        }

        saveProduct(name, description, brand, company, categories, sources);
    }
}
