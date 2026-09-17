package de.heinzdanner.produktkompass.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Der Quellenname darf nicht leer sein.")
    @Size(max = 255, message = "Der Quellenname darf maximal 255 Zeichen lang sein.")
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank(message = "Die URL darf nicht leer sein.")
    @Size(max = 1000, message = "Die URL darf maximal 1000 Zeichen lang sein.")
    @Column(nullable = false, length = 1000)
    private String url;

    @Size(max = 255, message = "Der Typ darf maximal 255 Zeichen lang sein.")
    @Column(length = 255)
    private String type;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Source() {
    }

    public Source(String title, String url, String type, LocalDateTime verifiedAt) {
        this.title = title;
        this.url = url;
        this.type = type;
        this.verifiedAt = verifiedAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Source source)) return false;
        return Objects.equals(id, source.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
