package com.nhl.bootique;

import java.io.InputStream;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.nhl.bootique.config.ConfigurationFactory;
import com.nhl.bootique.config.ConfigurationSource;
import com.nhl.bootique.config.jackson.InPlaceLeftHandMerger;
import com.nhl.bootique.config.jackson.InPlaceMapOverrider;
import com.nhl.bootique.config.jackson.JsonNodeConfigurationBuilder;
import com.nhl.bootique.config.jackson.JsonNodeConfigurationFactory;
import com.nhl.bootique.config.jackson.JsonNodeJsonParser;
import com.nhl.bootique.config.jackson.JsonNodeYamlParser;
import com.nhl.bootique.config.jackson.MultiFormatJsonNodeParser;
import com.nhl.bootique.config.jackson.MultiFormatJsonNodeParser.ParserType;
import com.nhl.bootique.env.Environment;
import com.nhl.bootique.jackson.JacksonService;
import com.nhl.bootique.log.BootLogger;

/**
 * @since 0.17
 */
public class JsonNodeConfigurationFactoryProvider implements Provider<ConfigurationFactory> {

	private ConfigurationSource configurationSource;
	private Environment environment;
	private JacksonService jacksonService;
	private BootLogger bootLogger;

	@Inject
	public JsonNodeConfigurationFactoryProvider(ConfigurationSource configurationSource, Environment environment,
			JacksonService jacksonService, BootLogger bootLogger) {

		this.configurationSource = configurationSource;
		this.environment = environment;
		this.jacksonService = jacksonService;
		this.bootLogger = bootLogger;
	}

	protected JsonNode loadConfiguration(Map<String, String> properties, Map<String, String> vars) {

		// hopefully sharing the mapper between parsers is safe... Does it
		// change the state during parse?
		ObjectMapper textToJsonMapper = jacksonService.newObjectMapper();
		Map<ParserType, Function<InputStream, JsonNode>> parsers = new EnumMap<>(ParserType.class);
		parsers.put(ParserType.YAML, new JsonNodeYamlParser(textToJsonMapper));
		parsers.put(ParserType.JSON, new JsonNodeJsonParser(textToJsonMapper));

		Function<URL, JsonNode> parser = new MultiFormatJsonNodeParser(parsers, bootLogger);

		BinaryOperator<JsonNode> singleConfigMerger = new InPlaceLeftHandMerger(bootLogger);
		Function<JsonNode, JsonNode> overrider = new InPlaceMapOverrider(properties, true, '.');

		if (!vars.isEmpty()) {
			overrider = overrider.andThen(new InPlaceMapOverrider(vars, false, '_'));
		}

		return JsonNodeConfigurationBuilder.builder().parser(parser).merger(singleConfigMerger)
				.resources(configurationSource).overrider(overrider).build();
	}

	@Override
	public ConfigurationFactory get() {

		Map<String, String> vars = environment.frameworkVariables();
		Map<String, String> properties = environment.frameworkProperties();

		JsonNode rootNode = loadConfiguration(properties, vars);

		ObjectMapper jsonToObjectMapper = jacksonService.newObjectMapper();
		if (!vars.isEmpty()) {

			// switching to slower CI strategy for mapping properties...
			jsonToObjectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		}

		return new JsonNodeConfigurationFactory(rootNode, jsonToObjectMapper);
	}
}
