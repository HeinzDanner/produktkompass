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
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }

        Category milkAlternatives = saveCategory("Milchalternativen", "Pflanzliche Ersatzprodukte auf Basis von Hafer, Soja oder Kokos.");
        Category chocolate = saveCategory("Schokolade", "Süßwaren mit Kakao und Schokoladenbasis.");
        Category coffee = saveCategory("Kaffee", "Getränke und Produkte auf Kaffeebasis.");
        Category beverages = saveCategory("Getränke", "Erfrischungsgetränke und Getränkeprodukte.");

        Brand oatly = saveBrand("Oatly", "Schwedische Marke für Hafergetränke und pflanzliche Produkte.");
        Brand alpro = saveBrand("Alpro", "Internationale Pflanzenmilch-Marke mit Soja-, Hafer- und Mandelbasis.");
        Brand lindt = saveBrand("Lindt & Sprüngli", "Schweizer Schokoladenmarke mit Fokus auf hochwertige Schokolade.");
        Brand ferrero = saveBrand("Ferrero", "Italienische Lebensmittelmarke mit vielfältigem Süßwarenportfolio.");
        Brand nestle = saveBrand("Nescafé", "Weltweite Kaffee-Marke von Nestlé.");
        Brand cocaCola = saveBrand("Coca-Cola", "Internationale Getränke-Marke mit Erfrischungsgetränken.");

        Company oatlyCompany = saveCompany(
                "Oatly Group AB",
                "https://www.oatly.com",
                "Schweden",
                "Schwedisches Unternehmen mit Fokus auf Haferprodukte."
        );
        Company alproCompany = saveCompany(
                "Danone S.A.",
                "https://www.danone.com",
                "Frankreich",
                "Internationale Lebensmittelgruppe mit mehreren Marken für pflanzliche Produkte."
        );
        Company lindtCompany = saveCompany(
                "Lindt & Sprüngli AG",
                "https://www.lindt-spruengli.com",
                "Schweiz",
                "Schweizer Schokoladenunternehmen mit globalem Vertrieb."
        );
        Company ferreroCompany = saveCompany(
                "Ferrero Group",
                "https://www.ferrero.com",
                "Italien",
                "Italienische Lebensmittelgruppe mit Süßwaren und Snacks."
        );
        Company nestleCompany = saveCompany(
                "Nestlé S.A.",
                "https://www.nestle.com",
                "Schweiz",
                "Multinationales Lebensmittel- und Getränkeunternehmen."
        );
        Company cocaColaCompany = saveCompany(
                "The Coca-Cola Company",
                "https://www.coca-cola.com",
                "USA",
                "Globales Getränkeunternehmen mit einer Vielzahl von Marken."
        );

        Source oatlySource = saveSource("Oatly Produktseite", "https://www.oatly.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(5));
        Source alproSource = saveSource("Alpro Produktübersicht", "https://www.alpro.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(10));
        Source lindtSource = saveSource("Lindt Produktübersicht", "https://www.lindt.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(7));
        Source ferreroSource = saveSource("Ferrero Produktseite", "https://www.ferrero.com/de/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(14));
        Source nestleSource = saveSource("Nescafé Produktseite", "https://www.nescafe.com/de", "Offizielle Produktseite", LocalDateTime.now().minusDays(9));
        Source cocaColaSource = saveSource("Coca-Cola Produktseite", "https://www.coca-cola.de", "Offizielle Produktseite", LocalDateTime.now().minusDays(11));

        saveProduct("Oat Drink Original", "Haferdrink mit natürlicher Süße und neutralem Geschmack.", oatly, oatlyCompany, Set.of(milkAlternatives), Set.of(oatlySource));
        saveProduct("Alpro Sojamilch", "Pflanzliche Milchalternative auf Sojabasis mit neutralem Geschmack.", alpro, alproCompany, Set.of(milkAlternatives), Set.of(alproSource));
        saveProduct("Lindt Excellence 70%", "Schokolade mit hohem Kakaoanteil und kräftigem Geschmack.", lindt, lindtCompany, Set.of(chocolate), Set.of(lindtSource));
        saveProduct("Nutella Original", "Süße Schokoladencreme mit Haselnuss- und Kakaogeschmack.", ferrero, ferreroCompany, Set.of(chocolate), Set.of(ferreroSource));
        saveProduct("Nescafé Gold", "Gemahlener Kaffee für klassische Zubereitungsformen.", nestle, nestleCompany, Set.of(coffee), Set.of(nestleSource));
        saveProduct("Coca-Cola Original", "Kohlensäurehaltiges Erfrischungsgetränk mit klassischem Cola-Geschmack.", cocaCola, cocaColaCompany, Set.of(beverages), Set.of(cocaColaSource));
    }

    private Brand saveBrand(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    private Category saveCategory(String name, String description) {
        return categoryRepository.save(new Category(name, description));
    }

    private Company saveCompany(String name, String website, String country, String description) {
        return companyRepository.save(new Company(name, website, country, description));
    }

    private Source saveSource(String title, String url, String type, LocalDateTime verifiedAt) {
        return sourceRepository.save(new Source(title, url, type, verifiedAt));
    }

    private void saveProduct(String name, String description, Brand brand, Company company, Set<Category> categories, Set<Source> sources) {
        Product product = new Product(name, description);
        product.setBrand(brand);
        product.setCompany(company);
        product.setCategories(categories);
        product.setSources(sources);
        productRepository.save(product);
    }
}
