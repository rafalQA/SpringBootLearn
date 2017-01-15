package com.possesor.service;

import com.possesor.exception.Message;
import com.possesor.exception.ValidationError;
import com.possesor.model.Currency;
import com.possesor.model.ForeignCurrency;
import com.possesor.model.Property;
import com.possesor.model.User;
import com.possesor.repository.PropertyRepository;
import com.possesor.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by Rafal Piotrowicz on 31.12.2016.
 */

@Service
public class PropertyService {

    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private UserRepository userRepository;

    public Long addPropertyForUser(Long userId, Property property) {
        validateAdd(property);
        userValidate(userId);
        validateForDataBase(property);

        User user = userRepository.findOne(userId);
        property.setUser(user);

        return propertyRepository.save(property).getId();
    }

    public BigDecimal getPropertyValueInForeignCurrency(Long id, ForeignCurrency foreignCurrency){

        UriComponents uriComponents = buildAndGetUriComponents(foreignCurrency);

        Currency currency = getCurrency(uriComponents);

        Property property = getPropertyByID(id);

        BigDecimal baseValue = property.getValue();

        BigDecimal rate = getRate(foreignCurrency, currency);

        return baseValue.multiply(rate);
    }

    public void deletePropertyWithUser(Long id) {
        Optional<Property> property = propertyRepository.findAll().stream()
                .filter(x -> x.getUser().getId().equals(id)).findFirst();

        if (property.isPresent()) {
            propertyRepository.delete(property.get().getId());
        }
    }

    public List<Property> getAllPropertiesForUser(Long id) {
        return propertyRepository.findAll().stream()
                .filter(x -> x.getUser().getId().equals(id)).collect(Collectors.toList());
    }

    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }

    private BigDecimal getRate(ForeignCurrency foreignCurrency, Currency currency) {
        return (BigDecimal) currency.getRates().getAdditionalProperties().entrySet().stream()
                .filter(x -> x.getKey().equals(foreignCurrency.name())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("in Api there is no currency: " + foreignCurrency))
                .getValue();
    }

    private Property getPropertyByID(Long id) {
        return propertyRepository.findAll().stream()
                .filter(x -> x.getUser().getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no property with Id: " + id));
    }

    private Currency getCurrency(UriComponents uriComponents) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(uriComponents.toUriString(), Currency.class);
    }

    private UriComponents buildAndGetUriComponents(ForeignCurrency foreignCurrency) {
        return UriComponentsBuilder.newInstance().
                scheme("http").host("api.fixer.io").path("latest")
                .queryParam("base", ForeignCurrency.PLN.name()).build();
    }

    private void validateAdd(Property property) {
        List<IllegalArgumentException> exceptions = new LinkedList<>();

        if (property.getId() != null) {
            exceptions.add(new IllegalArgumentException("id" + Message.NOT_ALLOWED));
        }

        if (property.getName() == null) {
            exceptions.add(new IllegalArgumentException("name" + Message.MAY_NOT_BE_NULL));
        }

        if (property.getName() != null && property.getName().equals("")) {
            exceptions.add(new IllegalArgumentException("name" + Message.MAY_NOT_BE_EMPTY));
        }

        if (property.getUser() != null) {
            exceptions.add(new IllegalArgumentException("user" + Message.NOT_ALLOWED));
        }

        if (property.getValue() == null) {
            exceptions.add(new IllegalArgumentException("value" + Message.MAY_NOT_BE_NULL));
        }

        if (property.getValue() != null && property.getValue().equals(BigDecimal.ZERO)) {
            exceptions.add(new IllegalArgumentException("value" + Message.MAY_NOT_BE_ZERO));
        }

        if (!exceptions.isEmpty()) {
            throw new ValidationError(exceptions);
        }
    }

    private void validateForDataBase(Property property) {
        if (propertyRepository.findByNameAndValueAndUser(
                property.getName(),
                property.getValue(), property.getUser()) != null) {
            throw new IllegalArgumentException("product" + Message.ALREADY_EXIST);
        }
    }

    private void userValidate(Long id) {
        if (userRepository.findOne(id) == null) {
            throw new IllegalArgumentException("user with id :" + id + "" + Message.NOT_FOUND);
        }
    }
}
