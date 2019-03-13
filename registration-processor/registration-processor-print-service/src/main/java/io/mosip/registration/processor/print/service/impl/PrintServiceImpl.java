package io.mosip.registration.processor.print.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.mosip.kernel.core.cbeffutil.jaxbclasses.BIRType;
import io.mosip.kernel.core.cbeffutil.jaxbclasses.SingleType;
import io.mosip.kernel.core.cbeffutil.spi.CbeffUtil;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.pdfgenerator.exception.PDFGeneratorException;
import io.mosip.kernel.core.qrcodegenerator.exception.QrcodeGenerationException;
import io.mosip.kernel.core.qrcodegenerator.spi.QrCodeGenerator;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.pdfgenerator.itext.constant.PDFGeneratorExceptionCodeConstant;
import io.mosip.kernel.qrcode.generator.zxing.constant.QrVersion;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.constant.IdType;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.UinCardType;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.notification.template.mapping.NotificationTemplate;
import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.JsonValue;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.identify.RegistrationProcessorIdentity;
import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.core.spi.print.service.PrintService;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.spi.uincardgenerator.UinCardGenerator;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.message.sender.exception.TemplateProcessingFailureException;
import io.mosip.registration.processor.message.sender.template.generator.TemplateGenerator;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import io.mosip.registration.processor.packet.storage.exception.FieldNotFoundException;
import io.mosip.registration.processor.packet.storage.exception.IdentityNotFoundException;
import io.mosip.registration.processor.packet.storage.exception.InstantanceCreationException;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.print.service.dto.JsonFileDTO;
import io.mosip.registration.processor.print.service.dto.JsonRequestDTO;
import io.mosip.registration.processor.print.service.exception.IDRepoResponseNull;
import io.mosip.registration.processor.print.service.exception.UINNotFoundInDatabase;
import io.mosip.registration.processor.print.service.kernel.dto.Documents;
import io.mosip.registration.processor.print.service.kernel.dto.IdResponseDTO;
import io.mosip.registration.processor.print.service.utility.UINCardConstant;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * The Class PrintServiceImpl.
 * 
 * @author M1048358 Alok
 */
@Service
public class PrintServiceImpl implements PrintService<Map<String, byte[]>> {

	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = "\\";

	/** The Constant LANGUAGE. */
	private static final String LANGUAGE = "language";

	/** The Constant VALUE. */
	private static final String VALUE = "value";

	/** The primary lang. */
	@Value("${primary.language}")
	private String primaryLang;

	/** The secondary lang. */
	@Value("${secondary.language}")
	private String secondaryLang;

	/** The Constant UIN_CARD_TEMPLATE. */
	private static final String UIN_CARD_TEMPLATE = "RPR_UIN_CARD_TEMPLATE";

	/** The Constant UIN. */
	private static final String UIN = "UIN";

	/** The Constant RID. */
	private static final String RID = "RID";

	/** The Constant FACE. */
	private static final String FACE = "Face";

	/** The Constant UIN_CARD_PDF. */
	private static final String UIN_CARD_PDF = "uinPdf";

	/** The Constant UIN_TEXT_FILE. */
	private static final String UIN_TEXT_FILE = "textFile";

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PrintServiceImpl.class);

	/** The primary language. */
	@Value("${mosip.primary-language}")
	private String langCode;

	/** The core audit request builder. */
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	/** The utility. */
	@Autowired
	private Utilities utility;

	/** The reg processor identity json. */
	@Autowired
	private RegistrationProcessorIdentity regProcessorIdentityJson;

	/** The demographic identity. */
	private JSONObject demographicIdentity = null;

	/** The attributes. */
	private Map<String, Object> attributes = new LinkedHashMap<>();

	/** The packet info manager. */
	@Autowired
	private PacketInfoManager<Identity, ApplicantInfoDto> packetInfoManager;

	/** The template generator. */
	@Autowired
	private TemplateGenerator templateGenerator;

	/** The uin card generator. */
	@Autowired
	private UinCardGenerator<ByteArrayOutputStream> uinCardGenerator;

	/** The rest client service. */
	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/** The qr code generator. */
	@Autowired
	private QrCodeGenerator<QrVersion> qrCodeGenerator;

	/** The qr string. */
	private StringBuilder qrString = new StringBuilder();

	/** The is transactional. */
	private boolean isTransactionSuccessful = false;

	/** The Constant INDIVIDUAL_BIOMETRICS. */
	private static final String INDIVIDUAL_BIOMETRICS = "individualBiometrics";

	private static final String RESOURCE = "src/main/resources/";

	private static final String APPLICANT_PHOTO = "ApplicantPhoto.png";

	private static final String QRCODE_PHOTO = "QrCode.png";

	/** The cbeffutil. */
	@Autowired
	private CbeffUtil cbeffutil;

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.processor.core.spi.print.service.PrintService#getPdf(
	 * java.lang.String)
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public Map<String, byte[]> getPdf(IdType idType, String idValue) {
		Map<String, byte[]> byteMap = new HashMap<>();
		String uin = null;
		String description = null;

		try {
			if (idType.toString().equalsIgnoreCase(UIN)) {
				uin = idValue;
			} else if (idType.toString().equalsIgnoreCase(RID)) {
				uin = packetInfoManager.getUINByRid(idValue).get(0);
				if (uin == null) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), uin,
							PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.name());
					throw new UINNotFoundInDatabase(PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.getCode());
				}
			}

			IdResponseDTO response = getIdRepoResponse(uin);

			boolean isPhotoSet = setApplicantPhoto(response);
			if (!isPhotoSet) {
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), uin,
						PlatformErrorMessages.RPR_PRT_APPLICANT_PHOTO_NOT_SET.name());
			}

			String jsonString = new JSONObject((Map) response.getResponse().getIdentity()).toString();

			setTemplateAttributes(jsonString);
			attributes.put(UINCardConstant.UIN, uin);

			boolean isQRcodeSet = setQrCode();
			if (!isQRcodeSet) {
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), uin,
						PlatformErrorMessages.RPR_PRT_QRCODE_NOT_SET.name());
			}

			// getting template and placing original values
			InputStream uinArtifact = templateGenerator.getTemplate(UIN_CARD_TEMPLATE, attributes, langCode);
			if (uinArtifact == null) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), idValue,
						PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.name());
				throw new TemplateProcessingFailureException(
						PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.getCode());
			}

			// generating pdf
			ByteArrayOutputStream pdf = uinCardGenerator.generateUinCard(uinArtifact, UinCardType.PDF);

			InputStream pdfStream = getpdfStream(pdf);
			byteMap.put(UIN_CARD_PDF, IOUtils.toByteArray(pdfStream));
			pdfStream.close();

			byte[] textFileByte = createTextFile();
			byteMap.put(UIN_TEXT_FILE, textFileByte);

			byte[] uinbyte = attributes.get(UINCardConstant.UIN).toString().getBytes();
			byteMap.put(UIN, uinbyte);

			isTransactionSuccessful = true;

		} catch (QrcodeGenerationException e) {
			description = "Error while QR Code Generation";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_QRCODE_NOT_GENERATED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (UINNotFoundInDatabase e) {
			description = "UIN not found in database for id" + idValue;
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (TemplateProcessingFailureException e) {
			description = "Error while Template Processing";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new TemplateProcessingFailureException(PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.getCode());

		} catch (PDFGeneratorException e) {
			description = "Error while pdf generation";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_PDF_NOT_GENERATED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (ApisResourceAccessException | IOException | ParseException
				| io.mosip.kernel.core.exception.IOException e) {
			description = "Internal error occured while processing packet id" + idValue;
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_PDF_GENERATION_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (Exception ex) {
			description = "Process stopped due to some internal error";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, description + ex.getMessage() + ExceptionUtils.getStackTrace(ex));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					ex.getMessage() + ExceptionUtils.getStackTrace(ex));

		} finally {
			String eventId = "";
			String eventName = "";
			String eventType = "";
			if (isTransactionSuccessful) {
				description = "Pdf generated and sent to print stage";
				eventId = EventId.RPR_402.toString();
				eventName = EventName.UPDATE.toString();
				eventType = EventType.BUSINESS.toString();
			} else {

				description = "Pdf was not generated for uin card template";
				eventId = EventId.RPR_405.toString();
				eventName = EventName.EXCEPTION.toString();
				eventType = EventType.SYSTEM.toString();
			}
			auditLogRequestBuilder.createAuditRequestBuilder(description, eventId, eventName, eventType, uin,
					ApiName.AUDIT);
		}

		return byteMap;
	}

	private InputStream getpdfStream(ByteArrayOutputStream pdf) {
		File pdfFile = new File(RESOURCE + attributes.get(UINCardConstant.UIN).toString() + ".pdf");
		InputStream fileStream = null;
		try (FileOutputStream op = new FileOutputStream(pdfFile);) {
			op.write(pdf.toByteArray());
			fileStream = new FileInputStream(pdfFile);
		} catch (IOException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, PlatformErrorMessages.RPR_PRT_PDF_GENERATION_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));
		}
		return fileStream;
	}

	/**
	 * Gets the id repo response.
	 *
	 * @param uin
	 *            the uin
	 * @return the id repo response
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */
	private IdResponseDTO getIdRepoResponse(String uin) throws ApisResourceAccessException {
		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(uin);

		String queryParamName = "type";
		String queryParamValue = "all";

		IdResponseDTO response = (IdResponseDTO) restClientService.getApi(ApiName.IDREPOSITORY, pathsegments,
				queryParamName, queryParamValue, IdResponseDTO.class);

		if (response == null || response.getResponse() == null) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					uin, PlatformErrorMessages.RPR_PRT_IDREPO_RESPONSE_NULL.name());
			throw new IDRepoResponseNull(PlatformErrorMessages.RPR_PRT_IDREPO_RESPONSE_NULL.getCode());
		}

		return response;
	}

	/**
	 * Creates the text file.
	 *
	 * @return the byte[]
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws io.mosip.kernel.core.exception.IOException
	 */
	private byte[] createTextFile() throws IOException {
		byte[] jsonTextFileBytes = null;
		JsonFileDTO jsonDto = new JsonFileDTO();
		jsonDto.setId("mosip.registration.print.send");
		jsonDto.setVersion("1.0");
		jsonDto.setRequestTime(DateUtils.getUTCCurrentDateTimeString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

		JsonRequestDTO request = new JsonRequestDTO();
		request.setNameAra((String) attributes.get(UINCardConstant.NAME_ARA));
		request.setNameEng((String) attributes.get(UINCardConstant.NAME_ENG));
		request.setPhoneNumber((String) attributes.get(UINCardConstant.PHONENUMBER));
		request.setAddressLine1Ara((String) attributes.get(UINCardConstant.ADDRESSLINE1_ARA));
		request.setAddressLine1Eng((String) attributes.get(UINCardConstant.ADDRESSLINE1_ENG));
		request.setAddressLine2Ara((String) attributes.get(UINCardConstant.ADDRESSLINE2_ARA));
		request.setAddressLine2Eng((String) attributes.get(UINCardConstant.ADDRESSLINE2_ENG));
		request.setAddressLine3Ara((String) attributes.get(UINCardConstant.ADDRESSLINE3_ARA));
		request.setAddressLine3Eng((String) attributes.get(UINCardConstant.ADDRESSLINE3_ENG));
		request.setRegionAra((String) attributes.get(UINCardConstant.REGION_ARA));
		request.setRegionEng((String) attributes.get(UINCardConstant.REGION_ENG));
		request.setProvinceAra((String) attributes.get(UINCardConstant.PROVINCE_ARA));
		request.setProvinceEng((String) attributes.get(UINCardConstant.PROVINCE_ENG));
		request.setCityAra((String) attributes.get(UINCardConstant.CITY_ARA));
		request.setCityEng((String) attributes.get(UINCardConstant.CITY_ENG));
		request.setPostalCode((String) attributes.get(UINCardConstant.POSTALCODE));

		jsonDto.setRequest(request);

		File jsonText = new File(RESOURCE + attributes.get(UINCardConstant.UIN).toString() + ".txt");

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.writeValue(jsonText, jsonDto);

		InputStream fileStream = new FileInputStream(jsonText);
		jsonTextFileBytes = IOUtils.toByteArray(fileStream);
		fileStream.close();

		return jsonTextFileBytes;
	}

	/**
	 * Sets the qr code.
	 *
	 * @return true, if successful
	 * @throws QrcodeGenerationException
	 *             the qrcode generation exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private boolean setQrCode() throws QrcodeGenerationException, IOException {
		boolean isQRCodeSet = false;
		byte[] qrCodeBytes = null;
		qrCodeBytes = qrCodeGenerator.generateQrCode(qrString.toString(), QrVersion.V30);
		if (qrCodeBytes != null) {
			File qrCode = new File(RESOURCE + QRCODE_PHOTO);
			FileUtils.writeByteArrayToFile(qrCode, qrCodeBytes);
			isQRCodeSet = true;
		}

		return isQRCodeSet;
	}

	/**
	 * Sets the applicant photo.
	 *
	 * @param response
	 *            the response
	 * @return true, if successful
	 * @throws Exception
	 *             the exception
	 */
	private boolean setApplicantPhoto(IdResponseDTO response) throws Exception {
		String value = null;
		boolean isPhotoSet = false;

		if (response == null || response.getResponse() == null) {
			return Boolean.FALSE;
		}
		if (response.getResponse().getDocuments() != null) {
			List<Documents> documents = response.getResponse().getDocuments();
			for (Documents doc : documents) {
				if (doc.getCategory().equals(INDIVIDUAL_BIOMETRICS)) {
					value = doc.getValue();
					break;
				}
			}
		}
		if (value != null) {
			byte[] biometricBytes = CryptoUtil.decodeBase64(value);

			List<BIRType> bIRTypeList = cbeffutil.getBIRDataFromXML(biometricBytes);
			isPhotoSet = setPhoto(isPhotoSet, bIRTypeList);
		}

		return isPhotoSet;
	}

	/**
	 * Sets the photo.
	 *
	 * @param isPhotoSet
	 *            the is photo set
	 * @param bIRTypeList
	 *            the b IR type list
	 * @return true, if successful
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private boolean setPhoto(boolean isPhotoSet, List<BIRType> bIRTypeList) throws IOException {
		byte[] facebyte = null;
		for (BIRType type : bIRTypeList) {
			List<SingleType> singleTypeList = type.getBDBInfo().getType();
			boolean isFaceType = false;
			for (SingleType singletype : singleTypeList) {
				if (singletype.value().equalsIgnoreCase(FACE))
					isFaceType = true;
			}
			if (isFaceType) {
				facebyte = type.getBDB();
			} else {
				continue;
			}

			File applicantPhoto = new File(RESOURCE + APPLICANT_PHOTO);
			FileUtils.writeByteArrayToFile(applicantPhoto, facebyte);
			isPhotoSet = true;
		}

		return isPhotoSet;
	}

	/**
	 * Gets the artifacts.
	 *
	 * @param idJsonString
	 *            the id json string
	 * @return the artifacts
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws ParseException
	 *             the parse exception
	 */
	private void setTemplateAttributes(String idJsonString) throws IOException, ParseException {
		NotificationTemplate template = new NotificationTemplate();
		String getIdentityJsonString = Utilities.getJson(utility.getConfigServerFileStorageURL(),
				utility.getGetRegProcessorIdentityJson());

		regProcessorIdentityJson = (new ObjectMapper()).readValue(getIdentityJsonString,
				RegistrationProcessorIdentity.class);
		demographicIdentity = (JSONObject) JsonUtil.objectMapperReadValue(idJsonString, JSONObject.class);

		if (demographicIdentity == null)
			throw new IdentityNotFoundException(PlatformErrorMessages.RPR_PIS_IDENTITY_NOT_FOUND.getMessage());

		template.setFirstName(getJsonValues(regProcessorIdentityJson.getIdentity().getName().getValue()));
		template.setGender(getJsonValues(regProcessorIdentityJson.getIdentity().getGender().getValue()));
		template.setEmailID(
				(String) JsonUtil.getJSONValue(demographicIdentity,regProcessorIdentityJson.getIdentity().getEmail().getValue()));
		template.setPhoneNumber(
				(String) JsonUtil.getJSONValue(demographicIdentity,regProcessorIdentityJson.getIdentity().getPhone().getValue()));
		template.setDateOfBirth(
				(String) JsonUtil.getJSONValue(demographicIdentity,regProcessorIdentityJson.getIdentity().getDob().getValue()));
		template.setAddressLine1(getJsonValues(regProcessorIdentityJson.getIdentity().getAddressLine1().getValue()));
		template.setAddressLine2(getJsonValues(regProcessorIdentityJson.getIdentity().getAddressLine2().getValue()));
		template.setAddressLine3(getJsonValues(regProcessorIdentityJson.getIdentity().getAddressLine3().getValue()));
		template.setRegion(getJsonValues(regProcessorIdentityJson.getIdentity().getRegion().getValue()));
		template.setProvince(getJsonValues(regProcessorIdentityJson.getIdentity().getProvince().getValue()));
		template.setCity(getJsonValues(regProcessorIdentityJson.getIdentity().getCity().getValue()));
		template.setPostalCode(
				(String) JsonUtil.getJSONValue(demographicIdentity,regProcessorIdentityJson.getIdentity().getPostalCode().getValue()));

		setAtrributes(template);
	}

	/**
	 * Sets the atrributes.
	 *
	 * @param template
	 *            the new atrributes
	 */
	private void setAtrributes(NotificationTemplate template) {
		attributes.put(UINCardConstant.NAME_ENG, getParameter(template.getFirstName(), primaryLang));
		attributes.put(UINCardConstant.NAME_ARA, getParameter(template.getFirstName(), secondaryLang));
		attributes.put(UINCardConstant.GENDER_ENG, getParameter(template.getGender(), primaryLang));
		attributes.put(UINCardConstant.GENDER_ARA, getParameter(template.getGender(), secondaryLang));
		attributes.put(UINCardConstant.DATEOFBIRTH, template.getDateOfBirth());
		attributes.put(UINCardConstant.ADDRESSLINE1_ENG, getParameter(template.getAddressLine1(), primaryLang));
		attributes.put(UINCardConstant.ADDRESSLINE1_ARA, getParameter(template.getAddressLine1(), secondaryLang));
		attributes.put(UINCardConstant.ADDRESSLINE2_ENG, getParameter(template.getAddressLine2(), primaryLang));
		attributes.put(UINCardConstant.ADDRESSLINE2_ARA, getParameter(template.getAddressLine2(), secondaryLang));
		attributes.put(UINCardConstant.ADDRESSLINE3_ENG, getParameter(template.getAddressLine3(), primaryLang));
		attributes.put(UINCardConstant.ADDRESSLINE3_ARA, getParameter(template.getAddressLine3(), secondaryLang));
		attributes.put(UINCardConstant.REGION_ENG, getParameter(template.getRegion(), primaryLang));
		attributes.put(UINCardConstant.REGION_ARA, getParameter(template.getRegion(), secondaryLang));
		attributes.put(UINCardConstant.PROVINCE_ENG, getParameter(template.getProvince(), primaryLang));
		attributes.put(UINCardConstant.PROVINCE_ARA, getParameter(template.getProvince(), secondaryLang));
		attributes.put(UINCardConstant.CITY_ENG, getParameter(template.getCity(), primaryLang));
		attributes.put(UINCardConstant.CITY_ARA, getParameter(template.getCity(), secondaryLang));
		attributes.put(UINCardConstant.POSTALCODE, template.getPostalCode());
		attributes.put(UINCardConstant.PHONENUMBER, template.getPhoneNumber());
		attributes.put(UINCardConstant.EMAILID, template.getEmailID());

		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			qrString.append(entry.getKey());
			qrString.append(entry.getValue());
		}
	}

	/**
	 * Gets the parameter.
	 *
	 * @param jsonValues
	 *            the json values
	 * @param language
	 *            the language
	 * @return the parameter
	 */
	private String getParameter(JsonValue[] jsonValues, String language) {
		String parameter = null;
		if (jsonValues != null) {
			for (int count = 0; count < jsonValues.length; count++) {
				String lang = jsonValues[count].getLanguage();
				if (language.contains(lang)) {
					parameter = jsonValues[count].getValue();
					break;
				}
			}
		}
		return parameter;
	}

	/**
	 * Gets the json values.
	 *
	 * @param identityKey
	 *            the identity key
	 * @return the json values
	 */
	private JsonValue[] getJsonValues(Object identityKey) {
		JSONArray demographicJsonNode = null;
		if (demographicIdentity != null)
			demographicJsonNode = JsonUtil.getJSONArray(demographicIdentity, identityKey);

		return (demographicJsonNode != null) ? mapJsonNodeToJavaObject(JsonValue.class, demographicJsonNode) : null;
	}

	/**
	 * Map json node to java object.
	 *
	 * @param <T>
	 *            the generic type
	 * @param genericType
	 *            the generic type
	 * @param demographicJsonNode
	 *            the demographic json node
	 * @return the t[]
	 */
	@SuppressWarnings("unchecked")
	private <T> T[] mapJsonNodeToJavaObject(Class<? extends Object> genericType, JSONArray demographicJsonNode) {
		String language;
		String value;
		T[] javaObject = (T[]) Array.newInstance(genericType, demographicJsonNode.size());
		try {
			for (int i = 0; i < demographicJsonNode.size(); i++) {

				T jsonNodeElement = (T) genericType.newInstance();

				JSONObject objects = JsonUtil.getJSONObjectFromArray(demographicJsonNode, i);
				language = (String) objects.get(LANGUAGE);
				value = (String) objects.get(VALUE);

				Field languageField = jsonNodeElement.getClass().getDeclaredField(LANGUAGE);
				languageField.setAccessible(true);
				languageField.set(jsonNodeElement, language);

				Field valueField = jsonNodeElement.getClass().getDeclaredField(VALUE);
				valueField.setAccessible(true);
				valueField.set(jsonNodeElement, value);

				javaObject[i] = jsonNodeElement;
			}
		} catch (InstantiationException | IllegalAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, "Error while Creating Instance of generic type" + ExceptionUtils.getStackTrace(e));
			throw new InstantanceCreationException(PlatformErrorMessages.RPR_SYS_INSTANTIATION_EXCEPTION.getMessage(),
					e);

		} catch (NoSuchFieldException | SecurityException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, "no such field exception" + ExceptionUtils.getStackTrace(e));
			throw new FieldNotFoundException(PlatformErrorMessages.RPR_SYS_NO_SUCH_FIELD_EXCEPTION.getMessage(), e);

		}

		return javaObject;

	}

}
