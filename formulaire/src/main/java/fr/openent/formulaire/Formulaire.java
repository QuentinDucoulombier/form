package fr.openent.formulaire;

import fr.openent.form.core.constants.Constants;
import fr.openent.form.core.constants.Tables;
import fr.openent.formulaire.controllers.*;
import fr.openent.formulaire.cron.NotifyCron;
import fr.openent.formulaire.cron.RgpdCron;
import fr.openent.formulaire.service.impl.FormulaireRepositoryEvents;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.BaseServer;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;

import java.util.ArrayList;
import java.util.List;
import fr.wseduc.cron.CronTrigger;

import static fr.openent.form.core.constants.ConfigFields.*;
import static fr.openent.form.core.constants.Tables.DB_SCHEMA;

public class Formulaire extends BaseServer {
	private static final Logger log = LoggerFactory.getLogger(Formulaire.class);

	@Override
	public void start() throws Exception {
		super.start();

		Constants.MAX_RESPONSES_EXPORT_PDF = config.getInteger(MAX_RESPONSE_EXPORT_PDF, 100);
		Constants.MAX_USERS_SHARING = config.getInteger(MAX_USERS_SHARING, 65000);

		final EventBus eb = getEventBus(vertx);
		final TimelineHelper timelineHelper = new TimelineHelper(vertx, eb, config);
		final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Formulaire.class.getSimpleName());
		final Storage storage = new StorageFactory(vertx, config).getStorage();

		// Set RepositoryEvents implementation used to process events published for transition
		setRepositoryEvents(new FormulaireRepositoryEvents(vertx));


		// Create and parameter confs for all controllers using sharing system
		SqlConf distribConf = SqlConfs.createConf(DistributionController.class.getName());
		SqlConf formConf = SqlConfs.createConf(FormController.class.getName());
		SqlConf formElementConf = SqlConfs.createConf(FormElementController.class.getName());
		SqlConf questionChoiceConf = SqlConfs.createConf(QuestionChoiceController.class.getName());
		SqlConf questionConf = SqlConfs.createConf(QuestionController.class.getName());
		SqlConf responseConf = SqlConfs.createConf(ResponseController.class.getName());
		SqlConf responseFileConf = SqlConfs.createConf(ResponseFileController.class.getName());
		SqlConf sectionConf = SqlConfs.createConf(SectionController.class.getName());
		SqlConf sharingConf = SqlConfs.createConf(SharingController.class.getName());

		List<SqlConf> confs = new ArrayList<>();
		confs.add(distribConf);
		confs.add(formConf);
		confs.add(formElementConf);
		confs.add(questionChoiceConf);
		confs.add(questionConf);
		confs.add(responseConf);
		confs.add(responseFileConf);
		confs.add(sectionConf);
		confs.add(sharingConf);

		for (SqlConf conf : confs) {
			conf.setSchema(DB_SCHEMA);
			conf.setTable(Tables.FORM);
			conf.setShareTable(Tables.FORM_SHARES);
		}

		// Set crud service
		FormController formController = new FormController(eventStore, storage, eb);
		formController.setCrudService(new SqlCrudService(DB_SCHEMA, Tables.FORM, Tables.FORM_SHARES));

		// Set sharing service
		SharingController sharingController = new SharingController(timelineHelper);
		sharingController.setShareService(new SqlShareService(DB_SCHEMA, Tables.FORM_SHARES, eb, securedActions, null));


		// Init controllers
		addController(new DelegateController());
		addController(new DistributionController(timelineHelper));
		addController(new EventBusController());
		addController(new FolderController());
		addController(formController);
		addController(new FormElementController());
		addController(new FormulaireController(eventStore));
		addController(new MonitoringController());
		addController(new QuestionChoiceController());
		addController(new QuestionController());
		addController(new QuestionTypeController());
		addController(new ResponseController(storage));
		addController(new ResponseFileController(storage));
		addController(new SectionController());
		addController(sharingController);
		addController(new UtilsController(storage));

		// CRON
		RgpdCron rgpdCron = new RgpdCron(storage);
		new CronTrigger(vertx, config.getString(RGPD_CRON, "0 0 0 */1 * ? *")).schedule(rgpdCron);
		NotifyCron notifyCron = new NotifyCron(timelineHelper);
		new CronTrigger(vertx, config.getString(NOTIFY_CRON, "0 0 0 */1 * ? *")).schedule(notifyCron);
	}
}